package ffm.ffi.api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Zen.Liu
 * @since 2025-09-24
 */
public interface Platform {
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


        @Override
        public String toString() {
            return name().toLowerCase();
        }

        public static final OS CURRENT = determineOS();

        private static OS determineOS() {
            var OS_NAME = System.getProperty("os.name");
            var OS_FIRST_WORD = OS_NAME.split(" ")[0].toLowerCase();
            return switch (OS_FIRST_WORD) {
                case String s when s.startsWith("mac") || s.startsWith("darwin") -> DARWIN;
                case "linux" -> LINUX;
                case "sunos", "solaris" -> SOLARIS;
                case "aix" -> AIX;
                case String s when s.startsWith("os400") || s.startsWith("os/400") -> IBMI;
                case "openbsd" -> OPENBSD;
                case "freebsd" -> FREEBSD;
                case "dragonfly" -> DRAGONFLY;
                case "windows" -> WINDOWS;
                case "midnightbsd" -> MIDNIGHTBSD;
                default -> UNKNOWN;
            };
        }
    }

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


        @Override
        public String toString() {
            return name().toLowerCase();
        }

        public static final CPU CURRENT = determineCPU();

        private static CPU determineCPU() {
            var ARCH = System.getProperty("os.arch").toLowerCase();
            var ENDIAN = System.getProperty("sun.cpu.endian", "").toLowerCase();
            return switch (ARCH) {
                // x86 架构
                case "x86", "i386", "i86pc", "i686" -> I386;
                // x86_64 架构
                case "x86_64", "amd64" -> X86_64;
                // PowerPC 系列
                case "ppc", "powerpc" -> (OS.CURRENT == OS.IBMI) ? PPC64 : PPC;
                case "ppc64", "powerpc64" -> ("little".equals(ENDIAN)) ? PPC64LE : PPC64;
                case "ppc64le", "powerpc64le" -> PPC64LE;
                // 其他明确架构
                case "s390", "s390x" -> S390X;
                case "aarch64" -> AARCH64;
                case "arm", "armv7l" -> ARM;
                case "mips64", "mips64el" -> MIPS64EL;
                case "loongarch64" -> LOONGARCH64;
                case "riscv64" -> RISCV64;
                // 默认处理
                default -> {
                    for (CPU cpu : values()) {
                        if (cpu.name().equalsIgnoreCase(ARCH)) {
                            yield cpu;
                        }
                    }
                    yield UNKNOWN;
                }
            };
        }
    }

    /// for windows is UTF_16LE otherwise is UTF-8
    Charset OS_CHARSET = (OS.CURRENT == OS.WINDOWS)
            ? StandardCharsets.UTF_16LE
            : StandardCharsets.UTF_8;
}
