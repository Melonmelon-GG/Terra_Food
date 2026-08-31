package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.ReviewField;
import com.dayan.food.entity.enums.ReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.UserReviewItem;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.UserReviewItemMapper;
import com.dayan.food.service.UserReviewPresenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTests {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private UserReviewItemMapper userReviewItemMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AppUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppUserServiceImpl(
                appUserMapper,
                userReviewItemMapper,
                new UserReviewPresenter(userReviewItemMapper),
                passwordEncoder
        );
    }

    @Test
    void updateSignatureUpsertsPendingItemWithCurrentValue() {
        when(appUserMapper.findByUsername("tester")).thenReturn(user(1L, "tester", UserRole.USER));

        service.updateSignature("tester", "  有些滋味，值得反复回味。  ");

        verify(userReviewItemMapper).upsertPending(1L, ReviewField.SIGNATURE, "", "有些滋味，值得反复回味。");
    }

    @Test
    void submitDisplayNameUpsertsPendingItem() {
        when(appUserMapper.findByUsername("tester")).thenReturn(user(1L, "tester", UserRole.USER));

        service.submitDisplayName("tester", " 小饕 ");

        verify(userReviewItemMapper).upsertPending(1L, ReviewField.DISPLAY_NAME, "tester", "小饕");
    }

    @Test
    void approveSignatureAppliesPendingText() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user(9L, "admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user(2L, "user", UserRole.USER));
        when(userReviewItemMapper.findPendingByUserAndField(2L, ReviewField.SIGNATURE))
                .thenReturn(item(5L, ReviewField.SIGNATURE, "", "新签名"));
        when(appUserMapper.updateSignature(2L, "新签名")).thenReturn(1);
        when(userReviewItemMapper.approveItem(5L, "admin")).thenReturn(1);

        service.reviewItem(2L, ReviewField.SIGNATURE, ReviewStatus.APPROVED, "admin");

        verify(appUserMapper).updateSignature(2L, "新签名");
        verify(userReviewItemMapper).approveItem(5L, "admin");
    }

    @Test
    void approveDisplayNameRejectsWhenTakenByAnotherUser() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user(9L, "admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user(2L, "user", UserRole.USER));
        when(userReviewItemMapper.findPendingByUserAndField(2L, ReviewField.DISPLAY_NAME))
                .thenReturn(item(6L, ReviewField.DISPLAY_NAME, "user", "美食家"));
        // 他人已占用该昵称（登录别名歧义是硬伤，审批必须拒绝）。
        when(appUserMapper.findByUsernameOrDisplayName("美食家")).thenReturn(user(7L, "other", UserRole.USER));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewItem(2L, ReviewField.DISPLAY_NAME, ReviewStatus.APPROVED, "admin")
        );

        verify(appUserMapper, never()).updateDisplayName(2L, "美食家");
        verify(userReviewItemMapper, never()).approveItem(6L, "admin");
    }

    @Test
    void approveDisplayNameAppliesWhenNameIsFree() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user(9L, "admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user(2L, "user", UserRole.USER));
        when(userReviewItemMapper.findPendingByUserAndField(2L, ReviewField.DISPLAY_NAME))
                .thenReturn(item(6L, ReviewField.DISPLAY_NAME, "user", "美食家"));
        when(appUserMapper.updateDisplayName(2L, "美食家")).thenReturn(1);
        when(userReviewItemMapper.approveItem(6L, "admin")).thenReturn(1);

        service.reviewItem(2L, ReviewField.DISPLAY_NAME, ReviewStatus.APPROVED, "admin");

        verify(appUserMapper).updateDisplayName(2L, "美食家");
        verify(userReviewItemMapper).approveItem(6L, "admin");
    }

    @Test
    void rejectItemKeepsCurrentValue() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user(9L, "admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user(2L, "user", UserRole.USER));
        when(userReviewItemMapper.findPendingByUserAndField(2L, ReviewField.SIGNATURE))
                .thenReturn(item(5L, ReviewField.SIGNATURE, "旧签名", "新签名"));
        when(userReviewItemMapper.rejectItem(5L, "admin")).thenReturn(1);

        service.reviewItem(2L, ReviewField.SIGNATURE, ReviewStatus.REJECTED, "admin");

        verify(appUserMapper, never()).updateSignature(2L, "新签名");
        verify(userReviewItemMapper).rejectItem(5L, "admin");
    }

    @Test
    void reviewItemRejectsInvalidStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewItem(2L, ReviewField.SIGNATURE, ReviewStatus.PENDING, "admin")
        );

        verify(userReviewItemMapper, never()).approveItem(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(userReviewItemMapper, never()).rejectItem(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void reviewItemAlreadyHandledThrows() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user(9L, "admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user(2L, "user", UserRole.USER));
        when(userReviewItemMapper.findPendingByUserAndField(2L, ReviewField.SIGNATURE))
                .thenReturn(item(5L, ReviewField.SIGNATURE, "", "新签名"));
        when(appUserMapper.updateSignature(2L, "新签名")).thenReturn(1);
        when(userReviewItemMapper.approveItem(5L, "admin")).thenReturn(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewItem(2L, ReviewField.SIGNATURE, ReviewStatus.APPROVED, "admin")
        );
    }

    @Test
    void sealReviewNotOpenYet() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user(9L, "admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user(2L, "user", UserRole.USER));
        when(userReviewItemMapper.findPendingByUserAndField(2L, ReviewField.SEAL))
                .thenReturn(item(8L, ReviewField.SEAL, "", "自定义章"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewItem(2L, ReviewField.SEAL, ReviewStatus.APPROVED, "admin")
        );

        verify(userReviewItemMapper, never()).approveItem(8L, "admin");
    }

    @Test
    void primaryAdminCanPromoteUserToSubAdmin() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user(9L, "admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user(2L, "user", UserRole.USER));
        when(appUserMapper.updateRole(2L, UserRole.SUB_ADMIN)).thenReturn(1);

        service.setRole(2L, UserRole.SUB_ADMIN, "admin");

        verify(appUserMapper).updateRole(2L, UserRole.SUB_ADMIN);
    }

    @Test
    void subAdminCannotGrantRoles() {
        when(appUserMapper.findByUsername("helper")).thenReturn(user(4L, "helper", UserRole.SUB_ADMIN));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.setRole(2L, UserRole.SUB_ADMIN, "helper")
        );

        verify(appUserMapper, never()).updateRole(2L, UserRole.SUB_ADMIN);
    }

    @Test
    void subAdminCannotDeletePrimaryAdmin() {
        when(appUserMapper.findByUsername("helper")).thenReturn(user(4L, "helper", UserRole.SUB_ADMIN));
        when(appUserMapper.findById(1L)).thenReturn(user(1L, "admin", UserRole.ADMIN));

        assertThrows(IllegalArgumentException.class, () -> service.deleteById(1L, "helper"));

        verify(appUserMapper, never()).deleteById(1L);
    }

    @Test
    void listUsersClampsExtremePageToAvoidOffsetOverflow() {
        when(appUserMapper.findPage(199_990, 10)).thenReturn(List.of());
        when(userReviewItemMapper.findPendingByIds(List.of())).thenReturn(List.of());

        service.listUsers(Integer.MAX_VALUE, 10);

        verify(appUserMapper).findPage(199_990, 10);
    }

    private AppUser user(Long id, String username, UserRole role) {
        AppUser user = new AppUser(username, "encoded-password", username, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserReviewItem item(Long id, ReviewField field, String currentValue, String pendingValue) {
        UserReviewItem item = new UserReviewItem();
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "userId", 2L);
        ReflectionTestUtils.setField(item, "field", field);
        ReflectionTestUtils.setField(item, "currentValue", currentValue);
        ReflectionTestUtils.setField(item, "pendingValue", pendingValue);
        return item;
    }
}