package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.FoodImportRowDTO;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.po.Region;
import com.dayan.food.entity.vo.CityCenterVO;
import com.dayan.food.entity.vo.FoodImportIssueVO;
import com.dayan.food.entity.vo.FoodImportResultVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.RegionMapper;
import com.dayan.food.service.CityCenterService;
import com.dayan.food.service.FoodImportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FoodImportServiceImpl implements FoodImportService {

    private static final int MAX_IMPORT_ROWS = 2_000;
    private static final int MAX_REPORTED_ISSUES = 100;
    private static final String ANONYMOUS = "无名";

    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            ".*(省|市|区|县|镇|乡|街道|路|街|巷|弄|号|栋|楼|层|广场|商场|地铁|附近|交叉口|步行).*"
    );
    private static final Pattern BUSINESS_PATTERN = Pattern.compile(
            ".*(店|馆|楼|阁|坊|院|居|轩|餐厅|饭店|菜馆|酒家|火锅|烧烤|烤肉|面馆|茶餐厅|甜品|食堂|小吃|馄饨|生煎|拉面|酒吧|bar).*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            ".*(好吃|推荐|便宜|价格|味道|喜欢|一定|记得|可以|不要|别去|无敌|夯|绝了|踩雷|适合|人均|排队).*"
    );

    private final FoodMapper foodMapper;
    private final RegionMapper regionMapper;
    private final AppUserMapper appUserMapper;
    private final CityCenterService cityCenterService;

    public FoodImportServiceImpl(
            FoodMapper foodMapper,
            RegionMapper regionMapper,
            AppUserMapper appUserMapper,
            CityCenterService cityCenterService
    ) {
        this.foodMapper = foodMapper;
        this.regionMapper = regionMapper;
        this.appUserMapper = appUserMapper;
        this.cityCenterService = cityCenterService;
    }

    @Override
    @CacheEvict(cacheNames = {"foodLists", "regions"}, allEntries = true)
    @Transactional
    public FoodImportResultVO importSpreadsheet(MultipartFile file) {
        validateFile(file);

        var parsedRows = new ArrayList<FoodImportRowDTO>();
        var issues = new ArrayList<FoodImportIssueVO>();
        int totalRows;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            ParseResult parsed = parseWorkbook(workbook, issues);
            parsedRows.addAll(parsed.rows());
            totalRows = parsed.totalRows();
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取表格，请确认文件未损坏", exception);
        }

        int imported = 0;
        int duplicates = 0;
        int anonymous = 0;

        for (FoodImportRowDTO row : parsedRows) {
            Region region = findOrCreateRegion(row.province(), row.city());
            if (foodMapper.countDuplicate(row.name(), region.getId(), row.address()) > 0) {
                duplicates++;
                continue;
            }

            String creator = resolveCreator(row.username());
            if (ANONYMOUS.equals(creator)) {
                anonymous++;
            }

            var food = new Food(
                    row.name(),
                    region,
                    row.latitude(),
                    row.longitude(),
                    row.address(),
                    row.summary(),
                    row.story(),
                    row.ingredients(),
                    null,
                    creator
            );
            foodMapper.insert(food);
            imported++;
        }

        int skipped = totalRows - parsedRows.size();
        return new FoodImportResultVO(
                totalRows,
                imported,
                skipped,
                duplicates,
                anonymous,
                List.copyOf(issues)
        );
    }

    private ParseResult parseWorkbook(Workbook workbook, List<FoodImportIssueVO> issues) {
        var rows = new ArrayList<FoodImportRowDTO>();
        var formatter = new DataFormatter(Locale.CHINA);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        int totalRows = 0;

        for (Sheet sheet : workbook) {
            HeaderMapping headers = detectHeaders(sheet, formatter, evaluator);
            String currentProvince = "";
            String currentCity = "";

            for (int rowIndex = headers.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                if (totalRows >= MAX_IMPORT_ROWS) {
                    addIssue(issues, rowIndex + 1, "超过单次最多 2000 行的限制，后续内容未导入");
                    return new ParseResult(rows, totalRows);
                }

                Row poiRow = sheet.getRow(rowIndex);
                Map<Integer, String> cells = readCells(poiRow, formatter, evaluator);
                if (cells.isEmpty()) {
                    continue;
                }

                String provinceCell = value(cells, headers.column(ImportField.PROVINCE, 0));
                if (!provinceCell.isBlank()) {
                    currentProvince = cityCenterService.normalizeProvince(provinceCell);
                    currentCity = "";
                }

                if (isHeadingOnly(cells, provinceCell)) {
                    continue;
                }

                totalRows++;
                String cityCell = value(cells, headers.column(ImportField.CITY, 1));
                String explicitAddress = value(cells, headers.column(ImportField.ADDRESS, 2));
                CityCenterVO center = cityCenterService.resolve(
                        currentProvince,
                        cityCell,
                        collectLocationText(cells),
                        currentCity
                );
                if (currentProvince.isBlank() || center == null) {
                    addIssue(issues, rowIndex + 1, "无法识别省份或城市");
                    continue;
                }
                currentCity = center.city();

                NameCandidate nameCandidate = inferName(cells, headers);
                if (nameCandidate == null) {
                    addIssue(issues, rowIndex + 1, "无法从杂乱单元格中确认店名或美食名称");
                    continue;
                }

                AddressCandidate addressCandidate = inferAddress(cells, headers, nameCandidate.column());
                String address = addressCandidate == null
                        ? center.city() + "市中心"
                        : addressCandidate.value();
                String category = value(cells, headers.column(ImportField.CATEGORY, 5));
                String recommended = value(cells, headers.column(ImportField.RECOMMENDED, 6));
                String comment = value(cells, headers.column(ImportField.COMMENT, 7));
                String username = value(cells, headers.column(ImportField.USERNAME, -1));

                String summary = buildSummary(category, recommended, address);
                String story = buildStory(
                        cells,
                        comment,
                        Set.of(nameCandidate.column(), addressCandidate == null ? -1 : addressCandidate.column()),
                        headers
                );
                String ingredients = !recommended.isBlank()
                        ? recommended
                        : (!category.isBlank() ? category : "详见描述");

                BigDecimal latitude = parseCoordinate(
                        value(cells, headers.column(ImportField.LATITUDE, -1)),
                        center.latitude(),
                        -90,
                        90
                );
                BigDecimal longitude = parseCoordinate(
                        value(cells, headers.column(ImportField.LONGITUDE, -1)),
                        center.longitude(),
                        -180,
                        180
                );

                rows.add(new FoodImportRowDTO(
                        rowIndex + 1,
                        limit(currentProvince, 50),
                        limit(center.city(), 50),
                        limit(nameCandidate.value(), 100),
                        limit(address, 500),
                        limit(summary, 1000),
                        limit(story, 5_000),
                        limit(ingredients, 500),
                        latitude,
                        longitude,
                        limit(username, 50)
                ));
            }
        }

        return new ParseResult(rows, totalRows);
    }

    private HeaderMapping detectHeaders(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        HeaderMapping best = new HeaderMapping(-1, new EnumMap<>(ImportField.class));
        int bestScore = 0;
        int lastHeaderCandidate = Math.min(sheet.getLastRowNum(), 20);

        for (int rowIndex = 0; rowIndex <= lastHeaderCandidate; rowIndex++) {
            Map<Integer, String> cells = readCells(sheet.getRow(rowIndex), formatter, evaluator);
            var columns = new EnumMap<ImportField, Integer>(ImportField.class);
            for (Map.Entry<Integer, String> cell : cells.entrySet()) {
                ImportField field = identifyHeader(cell.getValue());
                if (field != null) {
                    columns.putIfAbsent(field, cell.getKey());
                }
            }
            if (columns.size() > bestScore) {
                best = new HeaderMapping(rowIndex, columns);
                bestScore = columns.size();
            }
        }

        return bestScore >= 2 ? best : new HeaderMapping(-1, new EnumMap<>(ImportField.class));
    }

    private ImportField identifyHeader(String rawHeader) {
        String header = clean(rawHeader).toLowerCase(Locale.ROOT);
        if (matches(header, "省", "省份", "province")) return ImportField.PROVINCE;
        if (matches(header, "市", "市区", "城市", "city")) return ImportField.CITY;
        if (matches(header, "地点", "地址", "位置", "address")) return ImportField.ADDRESS;
        if (matches(header, "店名", "餐厅", "商户", "名称", "菜品名", "美食名", "name")) return ImportField.NAME;
        if (matches(header, "类型", "类别", "品类", "type", "category")) return ImportField.CATEGORY;
        if (matches(header, "推荐美食", "推荐菜", "菜品", "美食", "食物")) return ImportField.RECOMMENDED;
        if (matches(header, "食客点评", "点评", "评价", "故事", "备注", "comment")) return ImportField.COMMENT;
        if (matches(header, "用户", "用户名", "上传者", "记录人", "创建人", "username")) return ImportField.USERNAME;
        if (matches(header, "纬度", "latitude", "lat")) return ImportField.LATITUDE;
        if (matches(header, "经度", "longitude", "lng", "lon")) return ImportField.LONGITUDE;
        return null;
    }

    private boolean matches(String value, String... aliases) {
        for (String alias : aliases) {
            if (value.equals(alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private NameCandidate inferName(Map<Integer, String> cells, HeaderMapping headers) {
        int explicitColumn = headers.column(ImportField.NAME, -1);
        String explicit = value(cells, explicitColumn);
        if (!explicit.isBlank()) {
            return new NameCandidate(explicitColumn, clean(explicit));
        }

        NameCandidate best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int column : List.of(2, 3, 4)) {
            String candidate = extractBusinessTail(value(cells, column));
            int score = nameScore(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = new NameCandidate(column, candidate);
            }
        }
        return bestScore >= 2 && best != null && !best.value().isBlank() ? best : null;
    }

    private String extractBusinessTail(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return "";
        }
        if (looksLikeAddress(cleaned) && cleaned.contains(" ")) {
            String[] spaceParts = cleaned.split("\\s+");
            String tail = spaceParts[spaceParts.length - 1];
            if (tail.length() >= 2 && tail.length() <= 50 && BUSINESS_PATTERN.matcher(tail).matches()) {
                return tail;
            }
        }
        return cleaned;
    }

    private int nameScore(String value) {
        if (value.isBlank() || value.length() > 100) {
            return -100;
        }
        int score = value.length() >= 2 && value.length() <= 40 ? 2 : 0;
        if (BUSINESS_PATTERN.matcher(value).matches()) score += 4;
        if (looksLikeAddress(value)) score -= 5;
        if (COMMENT_PATTERN.matcher(value).matches()) score -= 4;
        if (Set.of("小吃", "火锅", "面食", "中餐", "西餐", "快餐", "自助").contains(value)) score -= 4;
        return score;
    }

    private AddressCandidate inferAddress(Map<Integer, String> cells, HeaderMapping headers, int nameColumn) {
        int explicitColumn = headers.column(ImportField.ADDRESS, -1);
        String explicit = value(cells, explicitColumn);
        if (!explicit.isBlank() && explicitColumn != nameColumn && looksLikeAddress(explicit)) {
            return new AddressCandidate(explicitColumn, clean(explicit));
        }

        AddressCandidate best = null;
        int bestScore = 0;
        for (int column : List.of(2, 3, 4)) {
            if (column == nameColumn) continue;
            String candidate = clean(value(cells, column));
            int score = addressScore(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = new AddressCandidate(column, candidate);
            }
        }
        return best;
    }

    private int addressScore(String value) {
        if (value.isBlank()) return 0;
        int score = looksLikeAddress(value) ? 5 : 0;
        if (value.matches(".*\\d+.*")) score += 2;
        if (BUSINESS_PATTERN.matcher(value).matches()) score -= 2;
        if (COMMENT_PATTERN.matcher(value).matches()) score -= 3;
        return score;
    }

    private boolean looksLikeAddress(String value) {
        return !value.isBlank() && ADDRESS_PATTERN.matcher(value).matches();
    }

    private String buildSummary(String category, String recommended, String address) {
        var parts = new ArrayList<String>();
        if (!category.isBlank()) parts.add("类型：" + clean(category));
        if (!recommended.isBlank()) parts.add("推荐：" + clean(recommended));
        if (!address.isBlank()) parts.add("地点：" + clean(address));
        return parts.isEmpty() ? "来自表格导入的地方美食记录。" : String.join("；", parts);
    }

    private String buildStory(
            Map<Integer, String> cells,
            String explicitComment,
            Set<Integer> excludedColumns,
            HeaderMapping headers
    ) {
        var pieces = new ArrayList<String>();
        var seen = new HashSet<String>();
        addStoryPiece(pieces, seen, explicitComment);

        Set<Integer> structuralColumns = new HashSet<>();
        structuralColumns.add(headers.column(ImportField.PROVINCE, 0));
        structuralColumns.add(headers.column(ImportField.CITY, 1));
        structuralColumns.add(headers.column(ImportField.CATEGORY, 5));
        structuralColumns.add(headers.column(ImportField.RECOMMENDED, 6));
        structuralColumns.add(headers.column(ImportField.USERNAME, -1));
        structuralColumns.add(headers.column(ImportField.LATITUDE, -1));
        structuralColumns.add(headers.column(ImportField.LONGITUDE, -1));
        for (Map.Entry<Integer, String> entry : cells.entrySet()) {
            int column = entry.getKey();
            if (column < 2 || excludedColumns.contains(column) || structuralColumns.contains(column)) {
                continue;
            }
            addStoryPiece(pieces, seen, entry.getValue());
        }
        return pieces.isEmpty() ? "暂无补充记录。" : "表格原始记录：" + String.join("；", pieces);
    }

    private void addStoryPiece(List<String> pieces, Set<String> seen, String value) {
        String cleaned = clean(value);
        if (!cleaned.isBlank() && seen.add(cleaned)) {
            pieces.add(cleaned);
        }
    }

    private String collectLocationText(Map<Integer, String> cells) {
        return String.join(" ", value(cells, 1), value(cells, 2), value(cells, 3), value(cells, 4));
    }

    private Map<Integer, String> readCells(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return Map.of();
        }
        var cells = new LinkedHashMap<Integer, String>();
        int lastCell = Math.min(Math.max(row.getLastCellNum(), 0), 30);
        for (int column = 0; column < lastCell; column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) continue;
            String value;
            try {
                value = formatter.formatCellValue(cell, evaluator);
            } catch (RuntimeException exception) {
                value = formatter.formatCellValue(cell);
            }
            if (value != null && !value.isBlank()) {
                cells.put(column, clean(value));
            }
        }
        return cells;
    }

    private boolean isHeadingOnly(Map<Integer, String> cells, String provinceCell) {
        if (cells.size() == 1 && !provinceCell.isBlank()) {
            return true;
        }
        String combined = String.join("", cells.values());
        return combined.contains("格子不够可以直接加") || combined.contains("表格有问题请联系管理员");
    }

    private String value(Map<Integer, String> cells, int column) {
        return column < 0 ? "" : cells.getOrDefault(column, "");
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t　]+", " ").trim();
    }

    private BigDecimal parseCoordinate(String value, BigDecimal fallback, double min, double max) {
        if (value.isBlank()) return fallback;
        try {
            BigDecimal coordinate = new BigDecimal(value.trim());
            double number = coordinate.doubleValue();
            return number >= min && number <= max ? coordinate : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private Region findOrCreateRegion(String province, String city) {
        Region existing = regionMapper.findByNameAndProvince(city, province);
        if (existing != null) {
            return existing;
        }
        var region = new Region(city, province, province + "省" + city + "地方美食");
        regionMapper.insert(region);
        return region;
    }

    private String resolveCreator(String identity) {
        if (identity == null || identity.isBlank()) {
            return ANONYMOUS;
        }
        AppUser user = appUserMapper.findByUsernameOrDisplayName(identity.trim());
        return user == null ? ANONYMOUS : user.getDisplayName();
    }

    private String limit(String value, int maxCodePoints) {
        String cleaned = clean(value);
        int count = cleaned.codePointCount(0, cleaned.length());
        if (count <= maxCodePoints) {
            return cleaned;
        }
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, maxCodePoints));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .xls 文件");
        }
    }

    private void addIssue(List<FoodImportIssueVO> issues, int rowNumber, String reason) {
        if (issues.size() < MAX_REPORTED_ISSUES) {
            issues.add(new FoodImportIssueVO(rowNumber, reason));
        }
    }

    private enum ImportField {
        PROVINCE,
        CITY,
        ADDRESS,
        NAME,
        CATEGORY,
        RECOMMENDED,
        COMMENT,
        USERNAME,
        LATITUDE,
        LONGITUDE
    }

    private record HeaderMapping(int rowIndex, EnumMap<ImportField, Integer> columns) {
        int column(ImportField field, int fallback) {
            return columns.getOrDefault(field, fallback);
        }
    }

    private record NameCandidate(int column, String value) {
    }

    private record AddressCandidate(int column, String value) {
    }

    private record ParseResult(List<FoodImportRowDTO> rows, int totalRows) {
    }
}
