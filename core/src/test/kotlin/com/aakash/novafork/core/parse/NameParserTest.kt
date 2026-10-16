package com.aakash.novafork.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NameParserTest(private val case: Case) {

    class Case(val fileName: String, val folders: List<String>, val expected: ParsedName) {
        override fun toString(): String = (folders.reversed() + fileName).joinToString("/")
    }

    @Test
    fun parses() {
        assertEquals(case.expected, NameParser.parse(case.fileName, case.folders))
    }

    companion object {
        private fun movie(
            fileName: String,
            title: String,
            year: Int? = null,
            resolution: String? = null,
            folders: List<String> = emptyList(),
        ) = Case(fileName, folders, ParsedName(ParsedKind.MOVIE, title, year, resolution = resolution))

        private fun episode(
            fileName: String,
            title: String,
            season: Int,
            episode: Int,
            year: Int? = null,
            episodeEnd: Int? = null,
            episodeTitle: String? = null,
            absolute: Int? = null,
            resolution: String? = null,
            folders: List<String> = emptyList(),
        ) = Case(
            fileName,
            folders,
            ParsedName(
                kind = ParsedKind.EPISODE,
                title = title,
                year = year,
                season = season,
                episode = episode,
                episodeEnd = episodeEnd,
                episodeTitle = episodeTitle,
                absoluteEpisode = absolute,
                resolution = resolution,
            ),
        )

        private fun daily(
            fileName: String,
            title: String,
            airDate: String,
            episodeTitle: String? = null,
            resolution: String? = null,
        ) = Case(
            fileName,
            emptyList(),
            ParsedName(ParsedKind.EPISODE, title, airDate = airDate, episodeTitle = episodeTitle, resolution = resolution),
        )

        private fun unknown(fileName: String, title: String, resolution: String? = null, folders: List<String> = emptyList()) =
            Case(fileName, folders, ParsedName(ParsedKind.UNKNOWN, title, resolution = resolution))

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Case> = listOf(
            // Movies: release names
            movie("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX.mkv", "Dune Part Two", 2024, "2160p"),
            movie("The Matrix (1999).mp4", "The Matrix", 1999),
            movie("Blade Runner 2049 (2017) [1080p].mkv", "Blade Runner 2049", 2017, "1080p"),
            movie("Blade.Runner.2049.2017.1080p.BluRay.x264.mkv", "Blade Runner 2049", 2017, "1080p"),
            movie("Inception.2010.1080p.BluRay.x264-SPARKS.mkv", "Inception", 2010, "1080p"),
            movie("Oppenheimer (2023) [2160p] [4K] [WEB] [5.1] [YTS.MX].mkv", "Oppenheimer", 2023, "2160p"),
            movie("Parasite (2019) [1080p] [BluRay] [5.1] [YTS.MX].mp4", "Parasite", 2019, "1080p"),
            movie("Mr.Hollands.Opus.1995.DVDRip.XviD.avi", "Mr. Hollands Opus", 1995),
            movie("Kill.Bill.Vol.1.2003.1080p.BluRay.mkv", "Kill Bill Vol. 1", 2003, "1080p"),
            movie("Mission.Impossible.Dead.Reckoning.Part.One.2023.IMAX.2160p.mkv", "Mission Impossible Dead Reckoning Part One", 2023, "2160p"),
            movie("Spider-Man.No.Way.Home.2021.HDR.2160p.WEB.H265-NAISU.mkv", "Spider-Man No Way Home", 2021, "2160p"),
            movie(
                "The.Lord.of.the.Rings.The.Return.of.the.King.2003.EXTENDED.1080p.BluRay.x264.mkv",
                "The Lord of the Rings The Return of the King",
                2003,
                "1080p",
            ),
            movie("Avengers Endgame 2019 Hindi Dubbed 720p HDRip.mkv", "Avengers Endgame", 2019, "720p"),
            movie("Joker.2019.2160p.UHD.BluRay.REMUX.HDR10+.HEVC.TrueHD.7.1.Atmos-FGT.mkv", "Joker", 2019, "2160p"),
            movie("Interstellar.2014.IMAX.1080p.AMZN.WEB-DL.DDP5.1.H.264-NTb.mkv", "Interstellar", 2014, "1080p"),
            movie("Tenet.2020.HDRip.XviD.AC3-EVO.avi", "Tenet", 2020),
            movie("Some.Movie.2018.UNRATED.DIRECTORS.CUT.BDRip.x264.mkv", "Some Movie", 2018),
            movie("Movie.Title.2019.PROPER.REPACK.1080p.mkv", "Movie Title", 2019, "1080p"),
            movie("Godzilla.Minus.One.2023.JAPANESE.1080p.WEBRip.mkv", "Godzilla Minus One", 2023, "1080p"),
            movie("Barbie 2023 1080p.mkv", "Barbie", 2023, "1080p"),
            movie("The.Batman.2022.4K.HMAX.WEB-DL.mkv", "The Batman", 2022, "2160p"),
            movie("Some.Movie.CAM.x264.mkv", "Some Movie"),
            movie("Some.Movie.720p.mkv", "Some Movie", resolution = "720p"),
            movie("Movie.2019-GRP.mkv", "Movie", 2019),
            movie("A.Quiet.Place.Part.II.2020.MULTi.1080p.mkv", "A Quiet Place Part II", 2020, "1080p"),
            movie("Pathaan.2023.Hindi.1080p.NF.WEB-DL.DD5.1.x264.mkv", "Pathaan", 2023, "1080p"),
            movie("RRR.2022.TELUGU.720p.HDRip.mkv", "RRR", 2022, "720p"),

            // Movies: titles that look like junk or years
            movie("2001.A.Space.Odyssey.1968.mkv", "2001 A Space Odyssey", 1968),
            movie("1917.2019.1080p.mkv", "1917", 2019, "1080p"),
            movie("2012.2009.mkv", "2012", 2009),
            movie("300.2006.mkv", "300", 2006),
            movie("Wonder.Woman.1984.2020.1080p.WEBRip.x264.mkv", "Wonder Woman 1984", 2020, "1080p"),
            movie("10.Cloverfield.Lane.2016.mkv", "10 Cloverfield Lane", 2016),
            movie("S.W.A.T.2003.720p.mkv", "S.W.A.T.", 2003, "720p"),
            movie("E.T.the.Extra-Terrestrial.1982.mkv", "E.T. the Extra-Terrestrial", 1982),
            movie("Mr. & Mrs. Smith (2005).mkv", "Mr. & Mrs. Smith", 2005),
            movie("Amélie (2001).mkv", "Amélie", 2001),
            movie("Charlotte's Web (2006).mkv", "Charlotte's Web", 2006),
            movie("The.French.Dispatch.2021.mkv", "The French Dispatch", 2021),
            movie("Dual (2022).mkv", "Dual", 2022),
            movie("Saw.X.2023.1080p.mkv", "Saw X", 2023, "1080p"),
            movie("WALL-E.2008.1080p.mkv", "WALL-E", 2008, "1080p"),
            movie("8-Bit Christmas (2021).mkv", "8-Bit Christmas", 2021),
            movie("Star.Wars.Episode.4.A.New.Hope.1977.mkv", "Star Wars Episode 4 A New Hope", 1977),
            movie("Wonder Woman 1984.mkv", "Wonder Woman", 1984),
            movie("The.Hateful.Eight.2015.70mm.mkv", "The Hateful Eight", 2015),
            movie("Toy.Story.3.2010.mkv", "Toy Story 3", 2010),
            movie("Se7en.1995.REMASTERED.1080p.mkv", "Se7en", 1995, "1080p"),
            movie("MOVIE.TITLE.2019.1080P.BLURAY.X264-GROUP.MKV", "MOVIE TITLE", 2019, "1080p"),
            movie("Movie (2019) (1080p BluRay x265 10bit).mkv", "Movie", 2019, "1080p"),
            movie("Top Gun - Maverick (2022).mkv", "Top Gun - Maverick", 2022),
            unknown("1917.mkv", "1917"),
            unknown("Blade Runner 2049.mkv", "Blade Runner 2049"),

            // Episodes: explicit markers
            episode("The.Office.US.S02E03.720p.mkv", "The Office US", 2, 3, resolution = "720p"),
            episode("breaking bad - 1x05 - Gray Matter.avi", "breaking bad", 1, 5, episodeTitle = "Gray Matter"),
            episode("Show S01E01E02.mkv", "Show", 1, 1, episodeEnd = 2),
            episode("Show.S01E01-E03.mkv", "Show", 1, 1, episodeEnd = 3),
            episode("Show.S01E01-02.mkv", "Show", 1, 1, episodeEnd = 2),
            episode("show.s1e2.mkv", "show", 1, 2),
            episode("Show.S01.E02.mkv", "Show", 1, 2),
            episode("Show Season 1 Episode 2.mkv", "Show", 1, 2),
            episode("Show.S01E01-720p.mkv", "Show", 1, 1, resolution = "720p"),
            episode("Fargo.2x07.Did.You.Do.This.mkv", "Fargo", 2, 7, episodeTitle = "Did You Do This"),
            episode(
                "Game.of.Thrones.S08E06.The.Iron.Throne.1080p.AMZN.WEB-DL.DDP5.1.H.264-GoT.mkv",
                "Game of Thrones",
                8,
                6,
                episodeTitle = "The Iron Throne",
                resolution = "1080p",
            ),
            episode("Doctor.Who.2005.S01E01.Rose.mkv", "Doctor Who", 1, 1, year = 2005, episodeTitle = "Rose"),
            episode("Mr.Robot.S01E01.mkv", "Mr. Robot", 1, 1),
            episode("Marvels.Agents.of.S.H.I.E.L.D.S01E01.mkv", "Marvels Agents of S.H.I.E.L.D.", 1, 1),
            episode("The.Mandalorian.S03E08.2160p.DSNP.WEB-DL.mkv", "The Mandalorian", 3, 8, resolution = "2160p"),
            episode(
                "Ted.Lasso.S03E12.So.Long.Farewell.2160p.ATVP.WEB-DL.DDP5.1.mkv",
                "Ted Lasso",
                3,
                12,
                episodeTitle = "So Long Farewell",
                resolution = "2160p",
            ),
            episode("Show.S01E02.HDTV.x264-LOL.mkv", "Show", 1, 2),
            episode("Show.S01E02-LOL.mkv", "Show", 1, 2),
            episode("1883.S01E01.mkv", "1883", 1, 1),
            episode("The.Office.US.(2005).S01E01.mkv", "The Office US", 1, 1, year = 2005),
            episode("The Office (US) - S01E01 - Pilot.mkv", "The Office US", 1, 1, episodeTitle = "Pilot"),
            episode("Seinfeld - S09E23E24 - The Finale.mkv", "Seinfeld", 9, 23, episodeEnd = 24, episodeTitle = "The Finale"),
            episode("friends_s10e17_the_last_one.avi", "friends", 10, 17, episodeTitle = "the last one"),
            episode("Kota Factory S02 E01.mkv", "Kota Factory", 2, 1),
            episode("Sacred Games - Season 2 Episode 3.mkv", "Sacred Games", 2, 3),
            episode("Twin Peaks (2017) S01E01.mkv", "Twin Peaks", 1, 1, year = 2017),
            episode("The.100.S01E01.mkv", "The 100", 1, 1),
            episode("9-1-1.S01E01.mkv", "9-1-1", 1, 1),
            episode("Show.Name.S2024E05.mkv", "Show Name", 2024, 5),

            // Anime and bare episode numbers
            episode(
                "[SubsPlease] Sousou no Frieren - 12 (1080p) [A1B2C3D4].mkv",
                "Sousou no Frieren",
                1,
                12,
                absolute = 12,
                resolution = "1080p",
            ),
            episode("Frieren - 12v2.mkv", "Frieren", 1, 12, absolute = 12),
            episode("Title - 12 (1080p).mkv", "Title", 1, 12, absolute = 12, resolution = "1080p"),
            episode("One Piece - 1071 [1080p].mkv", "One Piece", 1, 1071, absolute = 1071, resolution = "1080p"),
            episode("Naruto Shippuden Ep 245.mkv", "Naruto Shippuden", 1, 245, absolute = 245),
            episode("Naruto Shippuden Ep12.mkv", "Naruto Shippuden", 1, 12, absolute = 12),
            episode("[Erai-raws] Spy x Family S2 - 05 [1080p].mkv", "Spy x Family", 2, 5, resolution = "1080p"),
            episode("[Group] Title [05][720p].mkv", "Title", 1, 5, absolute = 5, resolution = "720p"),
            episode("[Group] Show 2nd Season - 03 [1080p].mkv", "Show", 2, 3, resolution = "1080p"),
            episode("The Office - 05 - Pilot.mkv", "The Office", 1, 5, absolute = 5, episodeTitle = "Pilot"),

            // Daily shows
            daily("The.Daily.Show.2024.05.12.mkv", "The Daily Show", "2024-05-12"),
            daily("Jimmy.Kimmel.Live.2024.03.15.Guest.Name.720p.WEB.mkv", "Jimmy Kimmel Live", "2024-03-15", "Guest Name", "720p"),
            daily("Last Week Tonight 12-05-2024.mkv", "Last Week Tonight", "2024-05-12"),

            // Folder context: show folders
            episode("03 - Title.mkv", "The Office", 2, 3, year = 2005, episodeTitle = "Title", folders = listOf("Season 2", "The Office (2005)")),
            episode("E05.mkv", "Severance", 1, 5, folders = listOf("Season 1", "Severance")),
            episode("05.mkv", "Severance", 1, 5, year = 2022, folders = listOf("Season 1", "Severance (2022)", "TV Shows")),
            episode("E01.mkv", "Doctor Who", 0, 1, folders = listOf("Specials", "Doctor Who")),
            episode("E05.mkv", "Show", 0, 5, folders = listOf("Season 00", "Show")),
            episode("Episode 5.mkv", "The Office", 3, 5, year = 2005, folders = listOf("Season 3", "The Office (2005)")),
            episode("Ep 05.mkv", "Show", 2, 5, folders = listOf("S02", "Show")),
            episode("Episode 5.mkv", "Sherlock", 2, 5, folders = listOf("Series 2", "Sherlock")),
            episode("S01E05.mkv", "Severance", 1, 5, folders = listOf("Season 1", "Severance")),
            episode("05 - Pilot.mkv", "The Office", 1, 5, year = 2005, episodeTitle = "Pilot", absolute = 5, folders = listOf("The Office (2005)")),
            episode("The.Office.S01E01.mkv", "The Office", 1, 1, year = 2005, folders = listOf("Season 1", "The Office (2005)")),
            episode("E05.mkv", "The Office US", 2, 5, folders = listOf("The.Office.US.S02.720p.WEB")),
            episode("Episode 3.mkv", "Breaking Bad", 3, 3, folders = listOf("Breaking Bad Season 3")),
            episode("E05.mkv", "E05", 1, 5, absolute = 5, folders = listOf("Downloads")),

            // Folder context: movie folders
            movie("movie.mkv", "Inception", 2010, folders = listOf("Inception (2010)")),
            movie("video.mp4", "Arrival", 2016, folders = listOf("Arrival (2016)", "Movies")),
            movie("film.mkv", "Heat", 1995, folders = listOf("Heat.1995.1080p.BluRay.x264-GRP")),
            movie("Inception.mkv", "Inception", 2010, folders = listOf("Inception (2010)")),
            movie("00000.m2ts", "Avatar", 2009, folders = listOf("STREAM", "BDMV", "Avatar (2009)")),
            movie("Some Clip.mkv", "Some Clip", folders = listOf("Movies")),
            movie("Some Clip.mkv", "Some Clip", folders = listOf("Action", "Films")),
            unknown("Some Clip.mkv", "Some Clip", folders = listOf("Home")),
            unknown("family trip.mp4", "family trip"),
            unknown("movie.mkv", "movie", folders = listOf("Downloads")),

            // Camera, phone and screen recordings
            unknown("VID_20240501_123456.mp4", "VID_20240501_123456"),
            unknown("IMG_1234.MOV", "IMG_1234"),
            unknown("PXL_20240101_101010123.mp4", "PXL_20240101_101010123"),
            unknown("MVI_1234.MOV", "MVI_1234"),
            unknown("DSC_0001.MOV", "DSC_0001"),
            unknown("DJI_0001.MP4", "DJI_0001"),
            unknown("GOPR0001.MP4", "GOPR0001"),
            unknown("GX010001.MP4", "GX010001"),
            unknown("VID-20240501-WA0001.mp4", "VID-20240501-WA0001"),
            unknown("WhatsApp Video 2024-05-01 at 12.34.56.mp4", "WhatsApp Video 2024-05-01 at 12.34.56"),
            unknown("Screen_Recording_20240501-123456_YouTube.mp4", "Screen_Recording_20240501-123456_YouTube"),
            unknown("Screenrecorder-2024-05-01-12-34-56-123.mp4", "Screenrecorder-2024-05-01-12-34-56-123"),
            unknown("20240501_123456.mp4", "20240501_123456"),
            unknown("2024-05-01 12-34-56.mp4", "2024-05-01 12-34-56"),
            unknown("VID_20240501_123456_1080p.mp4", "VID_20240501_123456_1080p", resolution = "1080p"),
            unknown("VID_20240501_123456.mp4", "VID_20240501_123456", folders = listOf("Movies")),
        )
    }
}
