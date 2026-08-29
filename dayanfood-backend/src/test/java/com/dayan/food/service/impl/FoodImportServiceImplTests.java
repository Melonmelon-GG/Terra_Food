package com.dayan.food.service.impl;

import com.dayan.food.entity.po.Region;
import com.dayan.food.entity.vo.CityCenterVO;
import com.dayan.food.entity.vo.FoodImportResultVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.RegionMapper;
import com.dayan.food.service.CityCenterService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodImportServiceImplTests {

    private static final String[] HEADERS = {"省份", "城市", "店名", "地址", "类型", "推荐美食", "食客点评"};
    private static final String[] CHENGDU_ROW = {"四川", "成都", "蜀香饭店", "春熙路12号", "川菜", "回锅肉", "好吃"};
    private static final Region CHENGDU = new Region("成都", "四川", "成都地方美食");

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private CityCenterService cityCenterService;

    private FoodImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoodImportServiceImpl(foodMapper, regionMapper, appUserMapper, cityCenterService);
    }

    @Test
    void importRejectsRegionsOutsideWhitelist() throws Exception {
        stubCityLookup();

        FoodImportResultVO result = service.importSpreadsheet(workbookFile(new String[][]{HEADERS, CHENGDU_ROW}));

        assertEquals(0, result.importedCount());
        assertEquals(1, result.invalidCount());
        assertTrue(
                result.issues().stream().anyMatch(issue -> issue.reason().contains("未收录白名单")),
                "未收录行应生成可见的识别提示"
        );
        verify(foodMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void importInsertsWhitelistedRowAndCountsAnonymous() throws Exception {
        stubCityLookup();
        when(regionMapper.findByNameAndProvince("成都", "四川")).thenReturn(CHENGDU);
        when(foodMapper.countDuplicate(anyString(), org.mockito.ArgumentMatchers.isNull(), anyString())).thenReturn(0);

        FoodImportResultVO result = service.importSpreadsheet(workbookFile(new String[][]{HEADERS, CHENGDU_ROW}));

        assertEquals(1, result.totalRows());
        assertEquals(1, result.importedCount());
        assertEquals(1, result.anonymousCount());
        assertEquals(0, result.invalidCount());
        verify(foodMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void importCountsDuplicatesWithoutInserting() throws Exception {
        stubCityLookup();
        when(regionMapper.findByNameAndProvince("成都", "四川")).thenReturn(CHENGDU);
        when(foodMapper.countDuplicate(anyString(), org.mockito.ArgumentMatchers.isNull(), anyString())).thenReturn(1);

        FoodImportResultVO result = service.importSpreadsheet(workbookFile(new String[][]{HEADERS, CHENGDU_ROW}));

        assertEquals(1, result.duplicateCount());
        assertEquals(0, result.importedCount());
        verify(foodMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void importCountsRowsTruncatedBeyondLimit() throws Exception {
        stubCityLookup();
        when(regionMapper.findByNameAndProvince("成都", "四川")).thenReturn(CHENGDU);
        when(foodMapper.countDuplicate(anyString(), org.mockito.ArgumentMatchers.isNull(), anyString())).thenReturn(0);

        // 表头 + 2005 条数据行：前 2000 条计入 totalRows，之后的 5 条计入截断统计。
        String[][] rows = new String[2006][];
        rows[0] = HEADERS;
        for (int i = 1; i < rows.length; i++) {
            rows[i] = CHENGDU_ROW;
        }

        FoodImportResultVO result = service.importSpreadsheet(workbookFile(rows));

        assertEquals(2000, result.totalRows());
        assertEquals(5, result.truncatedCount());
        assertTrue(
                result.issues().stream().anyMatch(issue -> issue.reason().contains("2000 行")),
                "截断应生成可见的识别提示"
        );
    }

    private void stubCityLookup() {
        when(cityCenterService.normalizeProvince("四川")).thenReturn("四川");
        when(cityCenterService.resolve(eq("四川"), eq("成都"), anyString(), anyString()))
                .thenReturn(new CityCenterVO("成都", "四川", new BigDecimal("30.6599"), new BigDecimal("104.0633")));
    }

    private MockMultipartFile workbookFile(String[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("珍馐");
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(rows[rowIndex][column]);
                }
            }
            var buffer = new ByteArrayOutputStream();
            workbook.write(buffer);
            return new MockMultipartFile(
                    "file",
                    "sample.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    buffer.toByteArray()
            );
        }
    }
}