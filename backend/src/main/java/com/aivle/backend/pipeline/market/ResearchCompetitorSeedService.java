package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ResearchCompetitorSeedService {
    static final int MAX_SEEDS = 8;
    private static final String EMPTY_WARNING = "경쟁 씨앗이 없습니다. 막지는 않지만 경쟁 관측이 업종 카테고리 중심으로 얇아질 수 있습니다.";
    private final ResearchCompetitorSeedRepository seeds;
    private final ObjectMapper mapper;
    public ResearchCompetitorSeedService(ResearchCompetitorSeedRepository seeds, ObjectMapper mapper) {
        this.seeds=seeds; this.mapper=mapper;
    }
    @Transactional(readOnly=true) public SeedsView current(Long projectId) {
        return view(seeds.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(projectId));
    }
    @Transactional public SeedsView replace(Long projectId, Long userId, JsonNode payload) {
        List<ResearchCompetitorSeed> existing=seeds.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(projectId);
        existing.forEach(ResearchCompetitorSeed::softDelete); seeds.saveAll(existing); seeds.flush();
        List<ResearchCompetitorSeed> saved=new ArrayList<>(); Set<String> names=new HashSet<>(); int order=1;
        JsonNode rows=payload==null?mapper.createArrayNode():payload;
        if(!rows.isArray()) throw invalid("경쟁 씨앗은 배열이어야 합니다.");
        for(JsonNode item:rows){
            String name=text(item,"name"),reason=text(item,"reason"),operator=text(item,"operatorName");
            if(name.isEmpty()&&reason.isEmpty()&&operator.isEmpty())continue;
            if(name.isEmpty()||reason.isEmpty())throw invalid("경쟁 씨앗은 이름과 경쟁 이유가 모두 필요합니다.");
            if(!names.add(name))throw invalid("같은 경쟁을 두 번 적었습니다: "+name);
            if(order>MAX_SEEDS)throw invalid("경쟁 씨앗은 최대 "+MAX_SEEDS+"개입니다.");
            saved.add(seeds.save(ResearchCompetitorSeed.create(UUID.randomUUID().toString(),projectId,order++,name,reason,operator,userId)));
        }
        return view(saved);
    }
    @Transactional(readOnly=true) public ObjectNode conceptBlock(Long projectId){
        List<ResearchCompetitorSeed> rows=seeds.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(projectId);
        if(rows.isEmpty())return null;
        ObjectNode block=mapper.createObjectNode(); block.put("_설명","사용자가 적은 경쟁·대체재 씨앗이며 조사 결과가 아닙니다.");
        ArrayNode items=block.putArray("seeds");
        for(ResearchCompetitorSeed row:rows){ObjectNode item=items.addObject();item.put("이름",row.getName());item.put("왜",row.getReason());if(row.getOperatorName()==null)item.putNull("운영사");else item.put("운영사",row.getOperatorName());}
        block.put("_운영사_칸","DART 조회용 법인명이며 모르면 비워 둡니다."); return block;
    }
    private SeedsView view(List<ResearchCompetitorSeed> rows){List<SeedView> items=rows.stream().map(row->new SeedView(row.getId(),row.getDisplayOrder(),row.getName(),row.getReason(),row.getOperatorName())).toList();return new SeedsView(items,items.isEmpty()?EMPTY_WARNING:null);}
    private static String text(JsonNode item,String field){JsonNode value=item.path(field);return value.isTextual()?value.asText().trim():"";}
    private static BusinessException invalid(String message){return new BusinessException(ErrorCode.VALIDATION_FAILED,message);}
    public record SeedView(String id,int displayOrder,String name,String reason,String operatorName){}
    public record SeedsView(List<SeedView> seeds,String warning){}
}
