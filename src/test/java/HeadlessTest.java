import com.mclauncher.core.*;
import java.nio.file.*;
import java.util.*;

/** 无头冒烟：JSON 往返、离线账号、maven 路径、Fabric/Quilt meta、Modrinth 搜索。 */
public class HeadlessTest {
    static int pass = 0, fail = 0;

    public static void main(String[] a) throws Exception {
        // 1. JSON 往返
        Object parsed = Json.parse("{\"name\":\"xyc\",\"n\":26.3,\"arr\":[1,2,3],\"ok\":true}");
        Map<String,Object> m = Json.obj(parsed);
        check("JSON 字符串", "xyc".equals(Json.str(m, "name")));
        check("JSON 数字", Math.abs(Json.num(m, "n").doubleValue() - 26.3) < 1e-9);
        check("JSON 数组", Json.arr(m, "arr").size() == 3);
        check("JSON 序列化往返", Json.parse(Json.stringify(m)) != null);

        // 2. 离线账号
        Account off = Account.offline("Steve");
        check("离线类型", "offline".equals(off.type));
        check("离线 UUID 36 位", off.uuid.length() == 36);

        // 3. maven 路径
        String rel = ModLoaderManager.mavenToPath("net.fabricmc:fabric-loader:0.16.14");
        check("maven 路径", rel.equals("net/fabricmc/fabric-loader/0.16.14/fabric-loader-0.16.14.jar"));

        // 4. 网络：Fabric/Quilt 版本
        try {
            List<String> fab = ModLoaderManager.listLoaderVersions(ModLoaderManager.FABRIC);
            check("Fabric 版本非空", !fab.isEmpty());
            List<String> quilt = ModLoaderManager.listLoaderVersions(ModLoaderManager.QUILT);
            check("Quilt 版本非空", !quilt.isEmpty());
        } catch (Exception e) { check("加载器 meta 网络: " + e.getMessage(), false); }

        // 5. 网络：Modrinth 搜索
        try {
            List<ModpackManager.SearchHit> hits = ModpackManager.search("fabric", 5);
            check("Modrinth 搜索非空", !hits.isEmpty());
        } catch (Exception e) { check("Modrinth 网络: " + e.getMessage(), false); }

        // 6. 配置加载/保存
        Path tmp = Files.createTempDirectory("xyc");
        Config cfg = Config.load(tmp);
        cfg.memoryMb = 4096;
        cfg.save();
        Config cfg2 = Config.load(tmp);
        check("配置持久化", cfg2.memoryMb == 4096);

        System.out.println("\n结果: 通过 " + pass + "，失败 " + fail);
        if (fail > 0) System.exit(1);
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        if (ok) pass++; else fail++;
    }
}
