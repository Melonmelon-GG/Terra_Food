package com.dayan.food.mapper;
import com.dayan.food.entity.vo.FoodTagVO;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface FoodTagMapper {
  List<FoodTagVO> findApproved(@Param("type") String type, @Param("keyword") String keyword);
  int insert(@Param("type") String type, @Param("name") String name, @Param("normalized") String normalized, @Param("userId") Long userId);
  int lockGovernance();
  int canAccessFood(@Param("foodId") Long foodId,@Param("userId") Long userId,@Param("admin") boolean admin);
  int migrateAliases(@Param("sourceId") Long sourceId,@Param("targetId") Long targetId);
  int recordMergeRelations(@Param("sourceId") Long sourceId,@Param("targetId") Long targetId,@Param("actorId") Long actorId);
  FoodTagVO findById(@Param("id") Long id);
  FoodTagVO findByIdForUpdate(@Param("id") Long id);
  FoodTagVO findByName(@Param("type") String type, @Param("normalized") String normalized);
  int countCreatedToday(@Param("userId") Long userId);
  int countUsable(@Param("tagIds") List<Long> tagIds, @Param("userId") Long userId);
  int countTypeForIds(@Param("tagIds") List<Long> tagIds, @Param("type") String type);
  int deleteLinks(@Param("foodId") Long foodId);
  int insertLinks(@Param("foodId") Long foodId, @Param("tagIds") List<Long> tagIds);
  List<FoodTagVO> findForFood(@Param("foodId") Long foodId, @Param("includePendingForUser") Long includePendingForUser, @Param("admin") boolean admin);
  List<FoodTagVO> findAdmin(@Param("status") String status, @Param("type") String type, @Param("keyword") String keyword,
                            @Param("offset") int offset, @Param("pageSize") int pageSize);
  int countAdmin(@Param("status") String status, @Param("type") String type, @Param("keyword") String keyword);
  int updateDefinition(@Param("id") Long id, @Param("name") String name, @Param("normalized") String normalized,
                       @Param("type") String type, @Param("status") String status, @Param("reviewedBy") Long reviewedBy,
                       @Param("version") int version);
  int migrateLinks(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
  int deleteLinksForTag(@Param("tagId") Long tagId);
  int markMerged(@Param("id") Long id, @Param("targetId") Long targetId, @Param("reviewedBy") Long reviewedBy,
                 @Param("version") int version);
  int insertAlias(@Param("tagId") Long tagId, @Param("alias") String alias, @Param("normalized") String normalized);
  int insertAudit(@Param("tagId") Long tagId, @Param("actorId") Long actorId,
                  @Param("action") String action, @Param("detail") String detail);
}
