/*
 * OverOS Lite V2 — PID 1 inicial (protótipo).
 * O initramfs deve montar o rootfs e executar este binário como PID 1.
 * Este programa não executa comandos recebidos pela rede.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/reboot.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static volatile sig_atomic_t power_action = 0;

enum { ACTION_REBOOT = 1, ACTION_POWEROFF = 2 };

static void on_signal(int sig) {
    if (sig == SIGUSR1) power_action = ACTION_REBOOT;
    if (sig == SIGUSR2 || sig == SIGTERM || sig == SIGINT) power_action = ACTION_POWEROFF;
}

static int ensure_dir(const char *path) {
    if (mkdir(path, 0755) == 0 || errno == EEXIST) return 0;
    fprintf(stderr, "overos-init: mkdir %s: %s\n", path, strerror(errno));
    return -1;
}

static int mount_fs(const char *source, const char *target,
                    const char *type, unsigned long flags) {
    if (mount(source, target, type, flags, NULL) == 0 || errno == EBUSY) return 0;
    fprintf(stderr, "overos-init: mount %s on %s: %s\n",
            type, target, strerror(errno));
    return -1;
}

static void setup_runtime(void) {
    (void)ensure_dir("/proc");
    (void)ensure_dir("/sys");
    (void)ensure_dir("/dev");
    (void)ensure_dir("/run");
    (void)ensure_dir("/tmp");
    (void)ensure_dir("/dev/pts");
    (void)mount_fs("proc", "/proc", "proc", MS_NOSUID | MS_NOEXEC | MS_NODEV);
    (void)mount_fs("sysfs", "/sys", "sysfs", MS_NOSUID | MS_NOEXEC | MS_NODEV);
    (void)mount_fs("devtmpfs", "/dev", "devtmpfs", MS_NOSUID);
    (void)mount_fs("tmpfs", "/run", "tmpfs", MS_NOSUID | MS_NODEV);
    (void)mount_fs("tmpfs", "/tmp", "tmpfs", MS_NOSUID | MS_NODEV);
    (void)mount_fs("devpts", "/dev/pts", "devpts", MS_NOSUID | MS_NOEXEC);
}

static pid_t launch_session(void) {
    pid_t pid = fork();
    if (pid < 0) {
        fprintf(stderr, "overos-init: fork: %s\n", strerror(errno));
        return -1;
    }
    if (pid == 0) {
        char *const argv[] = { (char *)"/sbin/overos-session", NULL };
        char *const envp[] = {
            (char *)"PATH=/sbin:/bin:/usr/sbin:/usr/bin",
            (char *)"HOME=/root",
            (char *)"SHELL=/bin/sh",
            (char *)"LANG=pt_BR.UTF-8",
            NULL
        };
        execve(argv[0], argv, envp);
        fprintf(stderr, "overos-init: não foi possível iniciar a sessão: %s\n",
                strerror(errno));
        _exit(127);
    }
    return pid;
}

int main(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = on_signal;
    sigemptyset(&sa.sa_mask);
    (void)sigaction(SIGUSR1, &sa, NULL);
    (void)sigaction(SIGUSR2, &sa, NULL);
    (void)sigaction(SIGTERM, &sa, NULL);
    (void)sigaction(SIGINT, &sa, NULL);

    if (getpid() != 1) {
        fprintf(stderr, "overos-init: aviso: executado fora do PID 1\n");
    }

    (void)sethostname("overos", 6);
    setup_runtime();

    for (;;) {
        int status = 0;
        pid_t child = launch_session();
        if (child < 0) {
            sleep(2);
            continue;
        }
        for (;;) {
            pid_t ended = waitpid(-1, &status, 0);
            if (ended == child || power_action) break;
            if (ended < 0 && errno == EINTR) continue;
            if (ended < 0 && errno == ECHILD) break;
            if (ended < 0) {
                fprintf(stderr, "overos-init: waitpid: %s\n", strerror(errno));
                break;
            }
            /* Recolhe também filhos órfãos adotados pelo PID 1. */
        }
        if (power_action) break;
        fprintf(stderr, "overos-init: sessão terminou (status=%d); reiniciando em 2s\n",
                status);
        sleep(2);
    }

    sync();
    if (power_action == ACTION_REBOOT) reboot(RB_AUTOBOOT);
    if (power_action == ACTION_POWEROFF) reboot(RB_POWER_OFF);
    reboot(RB_POWER_OFF);
    for (;;) pause();
}