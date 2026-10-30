/*
 * Nearly empty on purpose: this library exists for the file name it announces (see glibc_shim.h).
 * It names one function of the Android library it stands in for, so the linker keeps that library
 * as a dependency even when it is set to drop libraries that look unused.
 */
#pragma clang diagnostic ignored "-Wincompatible-library-redeclaration"
#pragma GCC diagnostic ignored "-Wbuiltin-declaration-mismatch"
extern void SHIM_SYMBOL(void);
void *const urinth_glibc_shim_ref = (void *) SHIM_SYMBOL;

void urinth_glibc_shim_stub(void) {}
