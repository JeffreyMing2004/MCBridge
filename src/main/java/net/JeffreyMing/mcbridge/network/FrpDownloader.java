package net.JeffreyMing.mcbridge.network;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FrpDownloader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FRP_VERSION = "0.67.0";
    private static final String BASE_URL = "https://github.com/fatedier/frp/releases/download/v" + FRP_VERSION + "/";

    public static File getFrpcBinary() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        String frpOs = "";
        String frpArch = "";
        String extension = "tar.gz";

        if (os.contains("win")) {
            frpOs = "windows";
            extension = "zip";
        } else if (os.contains("mac")) {
            frpOs = "darwin";
        } else if (os.contains("linux")) {
            frpOs = "linux";
        }

        if (arch.contains("64")) {
            frpArch = arch.contains("arm") || arch.contains("aarch64") ? "arm64" : "amd64";
        } else if (arch.contains("386") || arch.contains("x86")) {
            frpArch = "386";
        }

        if (frpOs.isEmpty() || frpArch.isEmpty()) {
            LOGGER.error("不支持的操作系统或架构: {} / {}", os, arch);
            return null;
        }

        String fileName = String.format("frp_%s_%s_%s", FRP_VERSION, frpOs, frpArch);
        String downloadUrl = BASE_URL + fileName + "." + extension;
        
        Path binDir = Paths.get("mcbridge", "bin");
        try {
            Files.createDirectories(binDir);
        } catch (IOException e) {
            LOGGER.error("创建二进制目录失败", e);
            return null;
        }

        String binaryName = frpOs.equals("windows") ? "frpc.exe" : "frpc";
        File binaryFile = binDir.resolve(binaryName).toFile();

        if (binaryFile.exists()) {
            return binaryFile;
        }

        LOGGER.info("正在从 {} 下载 frpc...", downloadUrl);
        try {
            downloadAndExtract(downloadUrl, binDir, fileName, extension, binaryName);
            if (binaryFile.exists()) {
                binaryFile.setExecutable(true);
                return binaryFile;
            }
        } catch (IOException e) {
            LOGGER.error("下载或解压 frpc 失败", e);
        }

        return null;
    }

    private static void downloadAndExtract(String urlStr, Path targetDir, String folderName, String extension, String binaryName) throws IOException {
        URL url = new URL(urlStr);
        Path archiveFile = targetDir.resolve(folderName + "." + extension);
        
        try (InputStream in = url.openStream()) {
            Files.copy(in, archiveFile, StandardCopyOption.REPLACE_EXISTING);
        }

        if (extension.equals("zip")) {
            extractZip(archiveFile, targetDir, binaryName);
        } else {
            extractTarGz(archiveFile, targetDir, binaryName);
        }
        
        // Clean up archive
        Files.deleteIfExists(archiveFile);
    }

    private static void extractZip(Path archiveFile, Path targetDir, String binaryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archiveFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(binaryName)) {
                    Files.copy(zis, targetDir.resolve(binaryName), StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static void extractTarGz(Path archiveFile, Path targetDir, String binaryName) throws IOException {
        try {
            // Use system tar command as a simple cross-platform way for macOS/Linux
            ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", archiveFile.toString(), "-C", targetDir.toString());
            pb.start().waitFor();
            
            // tar might extract it into a subdirectory, we need to move the binary
            // Typically: frp_0.67.0_linux_amd64/frpc
            // We'll search for it
            Files.walk(targetDir)
                 .filter(p -> p.getFileName().toString().equals(binaryName))
                 .findFirst()
                 .ifPresent(p -> {
                     try {
                         if (!p.getParent().equals(targetDir)) {
                             Files.move(p, targetDir.resolve(binaryName), StandardCopyOption.REPLACE_EXISTING);
                         }
                     } catch (IOException e) {
                         LOGGER.error("移动 frpc 失败", e);
                     }
                 });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
