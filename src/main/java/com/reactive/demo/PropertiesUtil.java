package com.reactive.demo;

import com.google.common.collect.Maps;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/***
 * 配置文件加载
 *
 * @author haikuo.zhk
 */
public class PropertiesUtil {

    private static final String PROPERTIES_PATH = "/proxyserver.properties";
    private static final String KEY_PROXY_MAPPING = "proxy.mapping";

    // 格式参考：proxy.mapping = /_proxy_1#10.1.15.131:80;/_proxy_2#10.1.15.131:80
    private static String proxyMapping;

    public static Map<String, String> URI_IPHOST_MAPPING = Maps.newHashMap();

    static {
        final InputStream stream = PropertiesUtil.class.getResourceAsStream(PROPERTIES_PATH);
        if(stream != null) {
            try {
                final Properties properties = new Properties();
                properties.load(stream);

                proxyMapping = properties.getProperty(KEY_PROXY_MAPPING);
                String[] mappings = proxyMapping.split(";");
                for(String router : mappings) {
                    String[] items = router.split("#");;
                    if(items != null && items.length == 2) {
                        URI_IPHOST_MAPPING.put(items[0], items[1]);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    stream.close();
                } catch (IOException ex) {
                }
            }
        }
    }
}
