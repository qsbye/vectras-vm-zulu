/* localsend-cli 在 proot/musl 环境的兼容垫片：
 * 1) __res_init/__res_ninit：glibc>=2.34 并入 libc.so.6 的私有符号，musl 没有，
 *    该二进制加载期引用，给空实现；
 * 2) getifaddrs/freeifaddrs：Android 禁止 app 域 bind netlink 路由套接字，
 *    musl 的 getifaddrs 走 netlink 必然失败，改用 ioctl(SIOCGIFCONF) 实现。
 * 仅供 LD_PRELOAD 注入 localsend-cli 使用，不影响系统其它程序。
 */
#define _GNU_SOURCE
#include <ifaddrs.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <net/if.h>
#include <netinet/in.h>

void __res_init(void) { }
void __res_ninit(void *s) { (void) s; }

static struct sockaddr *dup_addr(const struct ifreq *ifr)
{
    struct sockaddr_in *sa = calloc(1, sizeof(*sa));
    if (!sa) {
        return NULL;
    }
    *sa = *(const struct sockaddr_in *) &ifr->ifr_addr;
    sa->sin_family = AF_INET;
    return (struct sockaddr *) sa;
}

int getifaddrs(struct ifaddrs **out)
{
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        return -1;
    }
    char buf[8192];
    struct ifconf ifc;
    memset(&ifc, 0, sizeof(ifc));
    ifc.ifc_len = sizeof(buf);
    ifc.ifc_buf = buf;
    if (ioctl(fd, SIOCGIFCONF, &ifc) < 0) {
        close(fd);
        return -1;
    }

    struct ifaddrs *head = NULL, **tail = &head;
    int n = ifc.ifc_len / (int) sizeof(struct ifreq);
    for (int i = 0; i < n; i++) {
        const struct ifreq *ifr = &ifc.ifc_req[i];
        if (ifr->ifr_addr.sa_family != AF_INET) {
            continue;
        }
        int dup = 0;
        for (struct ifaddrs *p = head; p; p = p->ifa_next) {
            if (strcmp(p->ifa_name, ifr->ifr_name) == 0) {
                dup = 1;
                break;
            }
        }
        if (dup) {
            continue;
        }

        struct ifaddrs *ia = calloc(1, sizeof(*ia));
        if (!ia) {
            break;
        }
        ia->ifa_name = strdup(ifr->ifr_name);
        ia->ifa_addr = dup_addr(ifr);

        struct ifreq tmp;
        memset(&tmp, 0, sizeof(tmp));
        strncpy(tmp.ifr_name, ifr->ifr_name, IFNAMSIZ - 1);
        if (ioctl(fd, SIOCGIFFLAGS, &tmp) == 0) {
            ia->ifa_flags = (unsigned int) tmp.ifr_flags;
        }
        memset(&tmp, 0, sizeof(tmp));
        strncpy(tmp.ifr_name, ifr->ifr_name, IFNAMSIZ - 1);
        if (ioctl(fd, SIOCGIFNETMASK, &tmp) == 0) {
            ia->ifa_netmask = dup_addr(&tmp);
        }
        if (ia->ifa_flags & IFF_BROADCAST) {
            memset(&tmp, 0, sizeof(tmp));
            strncpy(tmp.ifr_name, ifr->ifr_name, IFNAMSIZ - 1);
            if (ioctl(fd, SIOCGIFBRDADDR, &tmp) == 0) {
                ia->ifa_broadaddr = dup_addr(&tmp);
            }
        }
        *tail = ia;
        tail = &ia->ifa_next;
    }
    close(fd);
    *out = head;
    return 0;
}

void freeifaddrs(struct ifaddrs *p)
{
    while (p) {
        struct ifaddrs *nx = p->ifa_next;
        free(p->ifa_name);
        free(p->ifa_addr);
        free(p->ifa_netmask);
        free(p->ifa_broadaddr);
        free(p);
        p = nx;
    }
}
