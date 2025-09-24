package ffm.ffi.api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Zen.Liu
 * @since 2025-09-24
 */
public interface Platform {
    ///  current OS type
    enum OS {
        DARWIN,
        FREEBSD,
        NETBSD,
        OPENBSD,
        DRAGONFLY,
        LINUX,
        SOLARIS,
        WINDOWS,
        AIX,
        IBMI,
        ZLINUX,
        MIDNIGHTBSD,
        UNKNOWN;

        public String toString() {
            return this.name().toLowerCase();
        }

        public static final OS CURRENT;

        static {
            CURRENT = determineOS();
        }

        static OS determineOS() {
            var osName = System.getProperty("os.name").split(" ")[0];
            if (!startsWithIgnoreCase(osName, "mac") && !startsWithIgnoreCase(osName, "darwin")) {
                if (startsWithIgnoreCase(osName, "linux")) {
                    return OS.LINUX;
                } else if (!startsWithIgnoreCase(osName, "sunos") && !startsWithIgnoreCase(osName, "solaris")) {
                    if (startsWithIgnoreCase(osName, "aix")) {
                        return OS.AIX;
                    } else if (!startsWithIgnoreCase(osName, "os400") && !startsWithIgnoreCase(osName, "os/400")) {
                        if (startsWithIgnoreCase(osName, "openbsd")) {
                            return OS.OPENBSD;
                        } else if (startsWithIgnoreCase(osName, "freebsd")) {
                            return OS.FREEBSD;
                        } else if (startsWithIgnoreCase(osName, "dragonfly")) {
                            return OS.DRAGONFLY;
                        } else if (startsWithIgnoreCase(osName, "windows")) {
                            return OS.WINDOWS;
                        } else {
                            return startsWithIgnoreCase(osName, "midnightbsd") ? OS.MIDNIGHTBSD : OS.UNKNOWN;
                        }
                    } else {
                        return OS.IBMI;
                    }
                } else {
                    return OS.SOLARIS;
                }
            } else {
                return OS.DARWIN;
            }
        }

        private static boolean startsWithIgnoreCase(String s1, String s2) {
            return s1.startsWith(s2) || s1.toUpperCase().startsWith(s2.toUpperCase()) || s1.toLowerCase()
                                                                                           .startsWith(
                                                                                                   s2.toLowerCase());
        }
    }

    ///  current OS CPU arch
    enum CPU {
        I386,
        X86_64,
        PPC,
        PPC64,
        PPC64LE,
        SPARC,
        SPARCV9,
        S390X,
        MIPS32,
        ARM,
        AARCH64,
        MIPS64EL,
        LOONGARCH64,
        RISCV64,
        UNKNOWN;

        public String toString() {
            return this.name().toLowerCase();
        }

        public static final CPU CURRENT;

        static {
            CURRENT = determineCPU();
        }

        static CPU determineCPU() {
            String archString = System.getProperty("os.arch");
            if (!"x86".equalsIgnoreCase(archString)
                    && !"i386".equalsIgnoreCase(archString)
                    && !"i86pc".equalsIgnoreCase(archString)
                    && !"i686".equalsIgnoreCase(archString)) {
                if (!"x86_64".equalsIgnoreCase(archString) && !"amd64".equalsIgnoreCase(archString)) {
                    if (!"ppc".equalsIgnoreCase(archString) && !"powerpc".equalsIgnoreCase(archString)) {
                        if (!"ppc64".equalsIgnoreCase(archString) && !"powerpc64".equalsIgnoreCase(archString)) {
                            if (!"ppc64le".equalsIgnoreCase(archString) && !"powerpc64le".equalsIgnoreCase(
                                    archString)) {
                                if (!"s390".equalsIgnoreCase(archString) && !"s390x".equalsIgnoreCase(archString)) {
                                    if ("aarch64".equalsIgnoreCase(archString)) {
                                        return CPU.AARCH64;
                                    } else if (!"arm".equalsIgnoreCase(archString) && !"armv7l".equalsIgnoreCase(
                                            archString)) {
                                        if (!"mips64".equalsIgnoreCase(archString) && !"mips64el".equalsIgnoreCase(
                                                archString)) {
                                            if ("loongarch64".equalsIgnoreCase(archString)) {
                                                return CPU.LOONGARCH64;
                                            } else if ("riscv64".equalsIgnoreCase(archString)) {
                                                return CPU.RISCV64;
                                            } else {
                                                for (CPU cpu : CPU.values()) {
                                                    if (cpu.name().equalsIgnoreCase(archString)) {
                                                        return cpu;
                                                    }
                                                }

                                                return CPU.UNKNOWN;
                                            }
                                        } else {
                                            return CPU.MIPS64EL;
                                        }
                                    } else {
                                        return CPU.ARM;
                                    }
                                } else {
                                    return CPU.S390X;
                                }
                            } else {
                                return CPU.PPC64LE;
                            }
                        } else {
                            return "little".equals(
                                    System.getProperty("sun.cpu.endian")) ? CPU.PPC64LE : CPU.PPC64;
                        }
                    } else {
                        return OS.IBMI.equals(OS.CURRENT) ? CPU.PPC64 : CPU.PPC;
                    }
                } else {
                    return CPU.X86_64;
                }
            } else {
                return CPU.I386;
            }
        }
    }

    /// for windows is UTF_16LE otherwise is UTF-8
    Charset OS_CHARSET = OS.CURRENT == OS.WINDOWS ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8;

}
