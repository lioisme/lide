#ifndef LIDE_DES_H
#define LIDE_DES_H

#include <stdint.h>
#include <stddef.h>

/* 探测 lide 加密文件魔数（"LIDE\x01\0\0\0"，须前 8 字节）。 */
int lide_des_magic(const uint8_t *p, size_t n);

/*
 * 解密 lide 加密字节码：magic || DES-ECB(K, PKCS#7(字节码))。
 * 返回 malloc 的明文缓冲区（成功时 *outlen 为去掉 PKCS#7 后的长度），
 * 失败返回 NULL。调用方负责 free()。
 */
uint8_t *lide_des_decrypt(const uint8_t *in, size_t inlen, size_t *outlen);

#endif /* LIDE_DES_H */