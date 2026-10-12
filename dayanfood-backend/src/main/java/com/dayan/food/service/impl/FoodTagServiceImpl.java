package com.dayan.food.service.impl;
import com.dayan.food.entity.dto.FoodTagCreateDTO;
import com.dayan.food.entity.vo.FoodTagVO;
import com.dayan.food.entity.vo.FoodTagPageVO;
import com.dayan.food.entity.dto.FoodTagAdminUpdateDTO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodTagMapper;
import com.dayan.food.service.FoodTagService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.text.Normalizer;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.cache.CacheManager;
import com.dayan.food.cache.CacheInvalidator;
@Service public class FoodTagServiceImpl implements FoodTagService {
 @org.springframework.beans.factory.annotation.Autowired(required=false) private com.dayan.food.cache.DiscoveryVersion discoveryVersion;
 private final FoodTagMapper mapper; private final AppUserMapper users; private final CacheManager caches; private final CacheInvalidator invalidator;
 public FoodTagServiceImpl(FoodTagMapper mapper, AppUserMapper users, CacheManager caches, CacheInvalidator invalidator){this.mapper=mapper;this.users=users;this.caches=caches;this.invalidator=invalidator;}
 public List<FoodTagVO> list(String type,String keyword){return mapper.findApproved(type==null?null:type.toUpperCase(),keyword==null?null:keyword.trim());}
 @Transactional public FoodTagVO create(FoodTagCreateDTO request,String username){
  String type=type(request.type()); var user=com.dayan.food.security.AuthenticatedActor.resolveForUpdate(users,username); String name=name(request.name()); String normalized=normalize(name);
  if(mapper.countCreatedToday(user.getId())>=20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"今天创建的待审核标签已达上限");
  try{mapper.insert(type,name,normalized,user.getId());}catch(DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"标签已存在");}
  FoodTagVO created=mapper.findByName(type,normalized); mapper.insertAudit(created.id(),user.getId(),"CREATE","用户创建待审核标签"); return created;
 }
 @Transactional(readOnly=true) public List<FoodTagVO> forFood(Long foodId,String username){
  var user=username==null?null:requireUser(username); Long userId=user==null?null:user.getId();
  boolean admin=user!=null && (user.getRole()==com.dayan.food.entity.enums.UserRole.ADMIN || user.getRole()==com.dayan.food.entity.enums.UserRole.SUB_ADMIN);
  if(mapper.canAccessFood(foodId,userId,admin)!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"菜品不存在");
  return mapper.findForFood(foodId,userId,admin);
 }
 @Transactional(readOnly=true) public List<Long> canonicalIds(List<Long> ids){
  if(ids==null)return List.of();
  return ids.stream().map(this::canonical).map(FoodTagVO::id).distinct().sorted().toList();
 }
 private FoodTagVO canonical(Long id){
  var seen=new java.util.HashSet<Long>(); FoodTagVO tag=requireTag(id);
  while("MERGED".equals(tag.status())){
   if(!seen.add(tag.id())||tag.mergedIntoId()==null||seen.size()>32)throw new ResponseStatusException(HttpStatus.CONFLICT,"标签合并链无效");
   tag=requireTag(tag.mergedIntoId());
  }
  return tag;
 }
 @Transactional public void replaceFoodTags(Long foodId,List<Long> tagIds,String username){
  var user=requireUser(username); mapper.lockGovernance(); List<Long> ids=canonicalIds(tagIds);
  if(ids.size()>30) throw new IllegalArgumentException("每道菜最多选择 30 个标签");
  var tags=ids.stream().map(this::requireTag).toList();
  for(var tag:tags)if(!"APPROVED".equals(tag.status())&&!("PENDING".equals(tag.status())&&java.util.Objects.equals(tag.createdBy(),user.getId())))throw new IllegalArgumentException("包含不存在、未通过或无权使用的标签");
  for(String type:List.of("TASTE","INGREDIENT","CUISINE"))if(tags.stream().filter(tag->type.equals(tag.type())).count()>10)throw new IllegalArgumentException("每类标签最多选择 10 个");
  mapper.deleteLinks(foodId); if(!ids.isEmpty()) mapper.insertLinks(foodId,ids); clearDiscovery();
 }
 @Transactional(readOnly=true) public FoodTagPageVO adminList(String status,String type,String keyword,int page,int pageSize){requireAdmin();int size=Math.min(Math.max(pageSize,1),50);int total=mapper.countAdmin(blank(status),blank(type),blank(keyword));int pages=Math.max(1,(int)Math.ceil((double)total/size));int p=Math.min(Math.max(page,1),pages);return new FoodTagPageVO(mapper.findAdmin(blank(status),blank(type),blank(keyword),(p-1)*size,size),total,p,size);}
 @Transactional public FoodTagVO adminUpdate(Long id,FoodTagAdminUpdateDTO request,String username){
  var actor=requireAdmin(); mapper.lockGovernance(); var existing=requireTag(id); String newName=name(request.name()); String newType=type(request.type()); String status=status(request.status());
  if("REJECTED".equals(status) && (request.reason()==null || request.reason().isBlank())) throw new IllegalArgumentException("拒绝标签必须填写原因");
  boolean dangerous=!existing.type().equals(newType)||"DISABLED".equals(status);
  if(dangerous && actor.getRole()!=com.dayan.food.entity.enums.UserRole.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅主管理员可以调整分类或停用标签");
  if(mapper.updateDefinition(id,newName,normalize(newName),newType,status,actor.getId(),request.version())!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"标签已被其他管理员修改");
  if(!existing.name().equals(newName)) mapper.insertAlias(id,existing.name(),normalize(existing.name()));
  mapper.insertAudit(id,actor.getId(),"UPDATE",auditDetail(snapshot(existing)+" -> "+newName+"/"+newType+"/"+status+" reason="+blank(request.reason()))); clearDiscovery(); return mapper.findById(id);
 }
 @Transactional public FoodTagVO merge(Long sourceId,Long targetId,int version,String username){
  if(sourceId.equals(targetId)) throw new IllegalArgumentException("标签不能合并到自身"); var actor=requireAdmin();mapper.lockGovernance();var source=requireTag(sourceId);var target=canonical(targetId);targetId=target.id();
  if(sourceId.equals(targetId)||"MERGED".equals(source.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"标签已合并或会形成循环");
  if(!source.type().equals(target.type())||!"APPROVED".equals(target.status())) throw new IllegalArgumentException("只能合并到同类型的已通过标签");
  mapper.recordMergeRelations(sourceId,targetId,actor.getId());mapper.migrateAliases(sourceId,targetId);mapper.migrateLinks(sourceId,targetId);mapper.deleteLinksForTag(sourceId);mapper.insertAlias(targetId,source.name(),normalize(source.name()));
  if(mapper.markMerged(sourceId,targetId,actor.getId(),version)!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"标签已被其他管理员修改");
  mapper.insertAudit(sourceId,actor.getId(),"MERGE",snapshot(source)+" -> "+snapshot(target));clearDiscovery();return mapper.findById(sourceId);
 }
 private com.dayan.food.entity.po.AppUser requireAdmin(){var user=requireUser(com.dayan.food.security.AuthenticatedActor.principal().username());if(user.getRole()!=com.dayan.food.entity.enums.UserRole.ADMIN&&user.getRole()!=com.dayan.food.entity.enums.UserRole.SUB_ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"需要管理员权限");return user;}
 private com.dayan.food.entity.po.AppUser requireUser(String username){var user=com.dayan.food.security.AuthenticatedActor.resolve(users,username);if(user==null||!user.isActive())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户不存在或已停用");return user;}
 private FoodTagVO requireTag(Long id){var tx=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()&&!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();var tag=tx?mapper.findByIdForUpdate(id):mapper.findById(id);if(tag==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"标签不存在");return tag;}
 private String auditDetail(String value){return value.length()>500?value.substring(0,500):value;}
 private String snapshot(FoodTagVO t){return "id="+t.id()+",v="+t.version()+",name="+t.name()+",type="+t.type()+",status="+t.status();}
 private String type(String value){String type=value==null?"":value.trim().toUpperCase();if(!List.of("TASTE","INGREDIENT","CUISINE").contains(type))throw new IllegalArgumentException("标签类型不合法");return type;}
 private String status(String value){String status=value==null?"":value.trim().toUpperCase();if(!List.of("PENDING","APPROVED","REJECTED","DISABLED").contains(status))throw new IllegalArgumentException("标签状态不合法");return status;}
 private String name(String value){String name=Normalizer.normalize(value.trim(),Normalizer.Form.NFKC).replaceAll("\\s+"," ");if(name.isBlank()||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("标签名称不合法");return name;}
 private String normalize(String value){return name(value).toLowerCase(java.util.Locale.ROOT);}
 private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
 private void clearDiscovery(){if(discoveryVersion!=null)discoveryVersion.advance();invalidator.clear(caches.getCache("foodDiscoveryCatalog"));invalidator.clear(caches.getCache("foodDiscoveryCounts"));invalidator.clear(caches.getCache("foodDiscoveryMap"));invalidator.clear(caches.getCache("wishlistMatches"));}
}
