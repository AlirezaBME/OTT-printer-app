#include <stdio.h>
#include <stdlib.h>
void *commCreateContext(char *a,char *b,char *c,char *d,int type,long mode) {
 fprintf(stderr,"REFERENCE CONTEXT queue=%s uri=%s root=%s type=%d mode=%ld\n",a,b,c,type,mode);
 return calloc(1,128);
}
void commDeleteContext(void *ctx) { free(ctx); }
int commJobInitialize(void *ctx,int plugin) {fprintf(stderr,"REFERENCE JOB INITIALIZE plugin=%d\n",plugin);return 0;}
int commJobStart(void *ctx) {fprintf(stderr,"REFERENCE JOB START\n");return 0;}
int commJobWrite(void *ctx,void *data,unsigned long *size) {return 0;}
int commJobEnd(void *ctx) {return 0;}
int commJobTerminate(void *ctx) {return 0;}
int commAdminInitialize(void *ctx,int plugin) {fprintf(stderr,"REFERENCE ADMIN INITIALIZE plugin=%d\n",plugin);return 0;}
int commAdminStart(void *ctx) {return 0;}
int commAdminEnd(void *ctx) {return 0;}
int commAdminTerminate(void *ctx) {return 0;}
int commAdminGetStatus(void *ctx,void *a,void *b,void *c) {return -1;}
int commAdminCtrl(void *ctx,void *a,void *b,void *c) {return -1;}
void *commAdminGetUtilHandle(void *ctx) {return 0;}
