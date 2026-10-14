import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.bukkit.plugin.PluginDescriptionFile;

// Run after mvn package, with Paper API and its dependencies on the classpath:
// java -ea --class-path "$CHECK_CLASSPATH" tests/NativeFeedbackCheck.java
// ponytail: descriptor/API and artifact checks; add live Paper/LuckPerms resolution before release.
class NativeFeedbackCheck {
    public static void main(String[] args) throws Exception {
        if (!NativeFeedbackCheck.class.desiredAssertionStatus()) {
            throw new IllegalStateException("Run with -ea");
        }
        String oldAliasName = "minecraft.adminbroadcast.receive";
        String nativeName = "minecraft.admin.command_feedback";
        Path resources = Path.of("src/main/resources");
        try (var jar = new ZipFile("target/VanillaPermissionsPlugin-2.1.0.jar")) {
            byte[] packaged = jar.getInputStream(jar.getEntry("plugin.yml")).readAllBytes();
            assert java.util.Arrays.equals(packaged, Files.readAllBytes(resources.resolve("plugin.yml")));
            var descriptor = new PluginDescriptionFile(new java.io.ByteArrayInputStream(packaged));
            assert descriptor.getPermissions().stream().noneMatch(p -> p.getName().equals(oldAliasName));
            assert descriptor.getPermissions().stream().noneMatch(p -> p.getName().equals(nativeName))
                : "Plugin must not redefine Paper's native permission/default";
            assert jar.getEntry("io/github/soul4723/vanillapermissions/listener/AdminBroadcastListener.class") == null;
            byte[] main = jar.getInputStream(jar.getEntry("io/github/soul4723/vanillapermissions/VanillaPermissionsPlugin.class")).readAllBytes();
            assert !new String(main, StandardCharsets.ISO_8859_1).contains("AdminBroadcastListener");
        }
        Path sources = Path.of("src/main/java/io/github/soul4723/vanillapermissions");
        assert !Files.exists(sources.resolve("listener/AdminBroadcastListener.java"));
        assert !Files.readString(sources.resolve("VanillaPermissionsPlugin.java")).contains("AdminBroadcastListener");
        System.out.println("PASS: Bukkit descriptor Permission objects/children/defaults, packaged descriptor, listener removal");
        System.out.println("NOT TESTED: registered Paper/LuckPerms resolution or live feedback/gamerules");
    }
}
