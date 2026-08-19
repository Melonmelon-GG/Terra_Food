package com.dayan.food.service.impl;

import com.dayan.food.entity.vo.CityCenterVO;
import com.dayan.food.service.CityCenterService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CityCenterServiceImpl implements CityCenterService {

    private static final Map<String, String> MUNICIPALITIES = Map.ofEntries(
            Map.entry("北京", "北京"),
            Map.entry("上海", "上海"),
            Map.entry("天津", "天津"),
            Map.entry("重庆", "重庆"),
            Map.entry("香港", "香港"),
            Map.entry("澳门", "澳门")
    );

    private static final Map<String, String> PROVINCE_CAPITALS = Map.ofEntries(
            Map.entry("河北", "石家庄"), Map.entry("山西", "太原"),
            Map.entry("内蒙古", "呼和浩特"), Map.entry("辽宁", "沈阳"),
            Map.entry("吉林", "长春"), Map.entry("黑龙江", "哈尔滨"),
            Map.entry("江苏", "南京"), Map.entry("浙江", "杭州"),
            Map.entry("安徽", "合肥"), Map.entry("福建", "福州"),
            Map.entry("江西", "南昌"), Map.entry("山东", "济南"),
            Map.entry("河南", "郑州"), Map.entry("湖北", "武汉"),
            Map.entry("湖南", "长沙"), Map.entry("广东", "广州"),
            Map.entry("广西", "南宁"), Map.entry("海南", "海口"),
            Map.entry("四川", "成都"), Map.entry("贵州", "贵阳"),
            Map.entry("云南", "昆明"), Map.entry("陕西", "西安"),
            Map.entry("甘肃", "兰州"), Map.entry("青海", "西宁"),
            Map.entry("新疆", "乌鲁木齐"), Map.entry("宁夏", "银川"),
            Map.entry("西藏", "拉萨"), Map.entry("台湾", "台北")
    );

    private final Map<String, CityCenterVO> centers;
    private final List<String> cityNamesByLength;

    public CityCenterServiceImpl() {
        this.centers = loadCenters();
        this.cityNamesByLength = centers.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    @Override
    public CityCenterVO resolve(String province, String cityText, String addressText, String previousCity) {
        String normalizedProvince = normalizeProvince(province);
        String municipality = MUNICIPALITIES.get(normalizedProvince);
        if (municipality != null) {
            return centers.get(municipality);
        }

        CityCenterVO direct = findInText(clean(cityText));
        if (direct == null) {
            direct = findInText(clean(addressText));
        }
        if (direct != null) {
            return direct;
        }

        if (previousCity != null && !previousCity.isBlank()) {
            CityCenterVO previous = centers.get(normalizeCity(previousCity));
            if (previous != null) {
                return previous;
            }
        }

        String capital = PROVINCE_CAPITALS.get(normalizedProvince);
        return capital == null ? null : centers.get(capital);
    }

    @Override
    public String normalizeProvince(String province) {
        if (province == null) {
            return "";
        }
        return clean(province)
                .replace("维吾尔自治区", "")
                .replace("壮族自治区", "")
                .replace("回族自治区", "")
                .replace("自治区", "")
                .replace("特别行政区", "")
                .replace("省", "")
                .replace("市", "");
    }

    private CityCenterVO findInText(String text) {
        if (text.isBlank()) {
            return null;
        }
        for (String city : cityNamesByLength) {
            if (text.contains(city)) {
                return centers.get(city);
            }
        }
        return null;
    }

    private String normalizeCity(String city) {
        return clean(city)
                .replace("自治州", "")
                .replace("地区", "")
                .replace("市", "");
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\s　]+", "").trim();
    }

    private Map<String, CityCenterVO> loadCenters() {
        var loaded = new LinkedHashMap<String, CityCenterVO>();
        var resource = new ClassPathResource("city-centers.csv");
        try (var reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .forEach(line -> addCenter(loaded, line));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取城市中心坐标", exception);
        }
        return loaded;
    }

    private void addCenter(Map<String, CityCenterVO> target, String line) {
        String[] values = line.split(",");
        if (values.length != 4) {
            return;
        }
        String city = normalizeCity(values[0]);
        target.put(city, new CityCenterVO(
                city,
                normalizeProvince(values[1]),
                new BigDecimal(values[2]),
                new BigDecimal(values[3])
        ));
    }

}
