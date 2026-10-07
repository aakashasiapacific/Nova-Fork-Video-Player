package com.aakash.novafork.sources

import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.data.SmbBrowser
import com.aakash.novafork.data.SmbCredentials
import com.aakash.novafork.data.SmbEntry
import com.aakash.novafork.data.SmbReadHandle
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.auth.NtlmAuthenticator
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.hierynomus.smbj.share.File as SmbFile

/** A folder or file on a share is missing or off limits; the server and share themselves work. */
class SmbPathException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * SMB browsing with smbj. Playback does not come through here (libVLC talks SMB itself); this
 * lists folders for scans and the Browse screen, tests new shares and reads artwork and subtitles.
 *
 * Only NTLM is offered: smbj's default SPNEGO/Kerberos authenticator needs javax.security classes
 * Android does not ship, and BouncyCastle provides the MD4 that NTLM needs. One authenticated
 * session per server and account is reused while its connection stays up.
 */
class SmbBrowserImpl(
    private val credentialsFor: suspend (host: String, share: String) -> SmbCredentials?,
) : SmbBrowser {
    private val client: SMBClient by lazy {
        SMBClient(
            SmbConfig.builder()
                .withSecurityProvider(BCSecurityProvider())
                .withAuthenticators(NtlmAuthenticator.Factory())
                .withMultiProtocolNegotiate(true)
                .withDfsEnabled(false)
                .withTimeout(15, TimeUnit.SECONDS)
                .withSoTimeout(20, TimeUnit.SECONDS)
                .build(),
        )
    }

    private val sessions = ConcurrentHashMap<SessionKey, Session>()
    private val sessionLocks = ConcurrentHashMap<SessionKey, Any>()

    /**
     * "host:port" → System.nanoTime() of the last failed connect. Artwork for a whole screen of
     * posters on a switched-off NAS must not queue up a connect timeout per poster.
     */
    private val unreachableSince = ConcurrentHashMap<String, Long>()

    private data class SessionKey(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val domain: String,
    ) {
        val isGuest: Boolean get() = username.isEmpty()

        // Never print the password.
        override fun toString(): String = "$username@$host:$port"
    }

    override suspend fun list(
        host: String,
        port: Int?,
        share: String,
        path: String,
        credentials: SmbCredentials,
    ): List<SmbEntry> = withContext(Dispatchers.IO) {
        val key = sessionKey(host, port, credentials)
        val dir = normalize(path)
        try {
            withShare(key, share, failFast = false) { disk ->
                disk.list(dir.toSmbPath()).mapNotNull { info -> info.toEntry(dir) }
            }
        } catch (e: Exception) {
            throw failure(e, key, host, share, dir, isFile = false)
        }
    }

    override suspend fun test(
        host: String,
        port: Int?,
        share: String,
        path: String,
        credentials: SmbCredentials,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val key = sessionKey(host, port, credentials)
        val dir = normalize(path)
        try {
            withShare(key, share, failFast = false) { disk ->
                if (dir.isNotEmpty() && !disk.folderExists(dir.toSmbPath())) throw SmbPathException("Folder not found: $dir")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(failure(e, key, host, share, dir, isFile = false))
        }
    }

    override suspend fun open(smbUri: String): SmbReadHandle = withContext(Dispatchers.IO) {
        val location = SmbUris.parse(smbUri) ?: throw IOException("Not an smb:// address: $smbUri")
        if (location.path.isEmpty()) throw IOException("Not a file: $smbUri")
        val credentials = credentialsFor(location.host, location.share) ?: SmbCredentials(null, null, null)
        val key = sessionKey(location.host, location.port, credentials)
        try {
            withShare(key, location.share, failFast = true) { disk ->
                val file = disk.openFile(
                    location.path.toSmbPath(),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
                try {
                    val length = file.fileInformation.standardInformation.endOfFile
                    SmbReadHandle(stream = SmbFileInputStream(file), length = length)
                } catch (e: Exception) {
                    file.closeSilently()
                    throw e
                }
            }
        } catch (e: Exception) {
            throw failure(e, key, location.host, location.share, location.path, isFile = true)
        }
    }

    /**
     * Runs [block] on the share, reconnecting once when a reused session turns out to be dead.
     * With [failFast], a server that could not be reached in the last few seconds is not tried again.
     */
    private fun <T> withShare(key: SessionKey, shareName: String, failFast: Boolean, block: (DiskShare) -> T): T {
        val cached = sessions[key]
        if (cached != null) {
            if (cached.connection.isConnected) {
                var share: DiskShare? = null
                try {
                    share = diskShare(cached, shareName)
                    return block(share)
                } catch (e: Exception) {
                    if (!isStale(e, cached, share)) throw e
                }
            }
            evict(key, cached)
        }
        return block(diskShare(session(key, failFast), shareName))
    }

    private fun session(key: SessionKey, failFast: Boolean): Session =
        synchronized(sessionLocks.getOrPut(key) { Any() }) {
            val existing = sessions[key]
            if (existing != null) {
                if (existing.connection.isConnected) return existing
                evict(key, existing)
            }
            val server = "${key.host}:${key.port}"
            val failedAt = unreachableSince[server]
            if (failFast && failedAt != null && System.nanoTime() - failedAt < UNREACHABLE_BACKOFF_NANOS) {
                throw IOException("${key.host} isn't reachable right now")
            }
            val connection = try {
                client.connect(key.host, key.port)
            } catch (e: IOException) {
                unreachableSince[server] = System.nanoTime()
                throw e
            }
            unreachableSince.remove(server)
            val session = try {
                authenticate(connection, key)
            } catch (e: Exception) {
                closeQuietly(connection)
                throw e
            }
            sessions[key] = session
            session
        }

    private fun authenticate(connection: Connection, key: SessionKey): Session {
        if (!key.isGuest) {
            val context = AuthenticationContext(key.username, key.password.toCharArray(), key.domain.ifEmpty { null })
            return connection.authenticate(context)
        }
        // Samba maps "Guest" to its guest account; some servers only accept an anonymous login.
        return try {
            connection.authenticate(AuthenticationContext.guest())
        } catch (e: Exception) {
            val status = e.ntStatus()
            if (status != NtStatus.STATUS_ACCESS_DENIED && !isLoginRejected(status)) throw e
            connection.authenticate(AuthenticationContext.anonymous())
        }
    }

    private fun diskShare(session: Session, name: String): DiskShare {
        val share = try {
            session.connectShare(name)
        } catch (e: SMBApiException) {
            throw ShareRefused(e)
        }
        return share as? DiskShare ?: throw IOException("$name is not a shared folder")
    }

    /** Marks errors of the tree connect: "not found" there means the share, not a folder in it. */
    private class ShareRefused(cause: SMBApiException) : IOException(cause.message, cause)

    private fun isStale(e: Exception, session: Session, share: DiskShare?): Boolean =
        !session.connection.isConnected ||
            share?.isConnected == false ||
            e.causes().any { cause ->
                cause is TransportException || (cause is SMBApiException && cause.status in SESSION_GONE)
            }

    private fun evict(key: SessionKey, session: Session) {
        if (!sessions.remove(key, session)) return
        val connection = session.connection
        if (connection.isConnected) {
            try {
                session.close()
            } catch (e: Exception) {
                // Already gone on the server side.
            }
        }
        closeQuietly(connection)
    }

    /** Releases this session's hold on the shared connection; smbj closes it once nobody uses it. */
    private fun closeQuietly(connection: Connection) {
        try {
            connection.close()
        } catch (e: Exception) {
            // Nothing left to clean up.
        }
    }

    /** Turns smbj and socket errors into messages people can act on. */
    private fun failure(
        e: Exception,
        key: SessionKey,
        host: String,
        share: String,
        path: String,
        isFile: Boolean,
    ): IOException {
        if (e is SmbPathException) return e
        val target = path.ifEmpty { share }
        val status = e.ntStatus()
        val shareRefused = e.causes().any { it is ShareRefused }
        if (isLoginRejected(status)) {
            val message = when {
                key.isGuest -> "$host needs a user name and password"
                status == NtStatus.STATUS_ACCOUNT_DISABLED -> "This account is disabled on $host"
                status == NtStatus.STATUS_PASSWORD_EXPIRED -> "The password has expired"
                status == NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED -> "$host doesn't let this account sign in over the network"
                else -> "Wrong user name or password"
            }
            return IOException(message, e)
        }
        when (status) {
            NtStatus.STATUS_BAD_NETWORK_NAME, NtStatus.STATUS_BAD_NETWORK_PATH ->
                return IOException("There's no share named $share on $host", e)
            NtStatus.STATUS_OBJECT_NAME_NOT_FOUND,
            NtStatus.STATUS_OBJECT_PATH_NOT_FOUND,
            NtStatus.STATUS_OBJECT_NAME_INVALID,
            NtStatus.STATUS_NO_SUCH_FILE,
            NtStatus.STATUS_NOT_A_DIRECTORY,
            NtStatus.STATUS_FILE_IS_A_DIRECTORY,
            -> return when {
                shareRefused -> IOException("There's no share named $share on $host", e)
                isFile -> SmbPathException("File not found: $target", e)
                else -> SmbPathException("Folder not found: $target", e)
            }
            NtStatus.STATUS_ACCESS_DENIED ->
                return when {
                    key.isGuest -> IOException("$share needs a user name and password", e)
                    shareRefused -> IOException("This account can't open $share on $host", e)
                    else -> SmbPathException("No permission to open $target", e)
                }
            NtStatus.STATUS_IO_TIMEOUT, NtStatus.STATUS_TIMEOUT ->
                return IOException("$host didn't answer", e)
            else -> Unit
        }
        val causes = e.causes().toList()
        return when {
            causes.any { it is UnknownHostException } -> IOException("Can't find $host on the network", e)
            causes.any { it is SocketTimeoutException || it is TimeoutException || it is NoRouteToHostException } ->
                IOException("$host didn't answer", e)
            causes.any { it is ConnectException } -> IOException("Can't connect to $host. Is file sharing turned on?", e)
            else -> IOException(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't open $share on $host", e)
        }
    }

    private fun isLoginRejected(status: NtStatus?): Boolean =
        status == NtStatus.STATUS_LOGON_FAILURE ||
            status == NtStatus.STATUS_ACCOUNT_DISABLED ||
            status == NtStatus.STATUS_PASSWORD_EXPIRED ||
            status == NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED

    private fun sessionKey(host: String, port: Int?, credentials: SmbCredentials): SessionKey {
        var username = credentials.username?.trim().orEmpty()
        var domain = credentials.domain?.trim().orEmpty()
        // People often type "WORKGROUP\user" into the user field.
        if (domain.isEmpty() && '\\' in username) {
            domain = username.substringBefore('\\')
            username = username.substringAfter('\\')
        }
        return SessionKey(
            host = host.trim().lowercase(Locale.ROOT),
            port = port ?: SMBClient.DEFAULT_PORT,
            username = username,
            password = if (username.isEmpty()) "" else credentials.password.orEmpty(),
            domain = domain,
        )
    }

    private fun FileIdBothDirectoryInformation.toEntry(dir: String): SmbEntry? {
        val name: String = fileName ?: return null
        if (name.isEmpty() || name.startsWith(".")) return null
        val attributes = fileAttributes
        if (attributes.has(FileAttributes.FILE_ATTRIBUTE_HIDDEN) || attributes.has(FileAttributes.FILE_ATTRIBUTE_SYSTEM)) {
            return null
        }
        return SmbEntry(
            name = name,
            path = if (dir.isEmpty()) name else "$dir/$name",
            isDirectory = attributes.has(FileAttributes.FILE_ATTRIBUTE_DIRECTORY),
            sizeBytes = endOfFile,
            modifiedAt = lastWriteTime?.toEpochMillis() ?: 0L,
        )
    }

    /** Closes the remote file handle together with the stream. */
    private class SmbFileInputStream(private val file: SmbFile) : FilterInputStream(file.inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                file.closeSilently()
            }
        }
    }

    private companion object {
        val UNREACHABLE_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(30)

        val SESSION_GONE = setOf(
            NtStatus.STATUS_USER_SESSION_DELETED,
            NtStatus.STATUS_NETWORK_SESSION_EXPIRED,
            NtStatus.STATUS_NETWORK_NAME_DELETED,
            NtStatus.STATUS_CONNECTION_DISCONNECTED,
            NtStatus.STATUS_CONNECTION_RESET,
        )

        fun normalize(path: String): String =
            path.replace('\\', '/').split('/').filter { it.isNotEmpty() }.joinToString("/")

        fun String.toSmbPath(): String = replace('/', '\\')

        fun Long.has(flag: FileAttributes): Boolean = (this and flag.value) != 0L

        fun Throwable.causes(): Sequence<Throwable> =
            generateSequence(this) { current -> current.cause?.takeIf { it !== current } }.take(10)

        fun Throwable.ntStatus(): NtStatus? =
            causes().firstNotNullOfOrNull { (it as? SMBApiException)?.status }
    }
}
