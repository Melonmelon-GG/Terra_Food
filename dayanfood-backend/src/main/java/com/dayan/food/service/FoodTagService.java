package com.dayan.food.service;
import com.dayan.food.entity.dto.FoodTagCreateDTO;
import com.dayan.food.entity.vo.FoodTagVO;
import com.dayan.food.entity.vo.FoodTagPageVO;
import com.dayan.food.entity.dto.FoodTagAdminUpdateDTO;
import java.util.List;
public interface FoodTagService {
    List<Long> canonicalIds(List<Long> ids);
    List<FoodTagVO> list(String type, String keyword);
    FoodTagVO create(FoodTagCreateDTO request, String username);
    List<FoodTagVO> forFood(Long foodId, String username);
    void replaceFoodTags(Long foodId, List<Long> tagIds, String username);
    FoodTagPageVO adminList(String status, String type, String keyword, int page, int pageSize);
    FoodTagVO adminUpdate(Long id, FoodTagAdminUpdateDTO request, String username);
    FoodTagVO merge(Long sourceId, Long targetId, int version, String username);
}
