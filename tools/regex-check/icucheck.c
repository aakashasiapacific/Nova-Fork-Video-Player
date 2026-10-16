/* Compiles regex patterns with ICU, the engine behind java.util.regex on Android. The JVM's own
   engine accepts things ICU rejects (a bare "}" for one), and a pattern that fails to compile there
   only breaks on the phone. Built and run by run.sh.
   Input: one pattern per record, records separated by a NUL byte, each "flags\tpattern" (UTF-8).
   Prints FAIL lines for patterns ICU rejects. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
typedef uint16_t UChar;
typedef struct { int32_t line; int32_t offset; UChar preContext[16]; UChar postContext[16]; } UParseError;
typedef int UErrorCode;
#define CAT2(a, b) a##_##b
#define CAT(a, b) CAT2(a, b)
#define ICU(name) CAT(name, ICU_VER)
void *ICU(uregex_open)(const UChar *pattern, int32_t len, uint32_t flags, UParseError *pe, UErrorCode *status);
void ICU(uregex_close)(void *re);
UChar *ICU(u_strFromUTF8)(UChar *dest, int32_t cap, int32_t *len, const char *src, int32_t srcLen, UErrorCode *status);
const char *ICU(u_errorName)(UErrorCode code);
int main(int argc, char **argv) {
  FILE *f = fopen(argv[1], "rb"); if (!f) return 2;
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  char *buf = malloc(n + 1); if (fread(buf, 1, n, f) != (size_t)n) return 2; buf[n] = 0;
  int total = 0, bad = 0; char *p = buf;
  while (p < buf + n) {
    char *rec = p; size_t rl = strlen(rec); p += rl + 1; if (!rl) continue;
    char *tab = strchr(rec, '\t'); if (!tab) continue; *tab = 0;
    uint32_t javaFlags = (uint32_t)atoi(rec); const char *pat = tab + 1;
    uint32_t flags = 0; /* Java -> ICU flag bits */
    if (javaFlags & 0x02) flags |= 2;    /* CASE_INSENSITIVE */
    if (javaFlags & 0x04) flags |= 4;    /* COMMENTS */
    if (javaFlags & 0x08) flags |= 8;    /* MULTILINE */
    if (javaFlags & 0x20) flags |= 32;   /* DOTALL */
    UChar u[8192]; int32_t ul = 0; UErrorCode st = 0;
    ICU(u_strFromUTF8)(u, 8192, &ul, pat, -1, &st);
    if (st > 0) { printf("UTF8 %s\n", pat); bad++; continue; }
    UParseError pe; memset(&pe, 0, sizeof pe); st = 0;
    void *re = ICU(uregex_open)(u, ul, flags, &pe, &st);
    total++;
    if (st > 0) { bad++; printf("FAIL %s at offset %d: %s\n", ICU(u_errorName)(st), pe.offset, pat); }
    if (re) ICU(uregex_close)(re);
  }
  printf("checked %d patterns, %d rejected by ICU\n", total, bad);
  return bad ? 1 : 0;
}
