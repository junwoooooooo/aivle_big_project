package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ResearchCompetitorSeedServiceTests {
    private final ResearchCompetitorSeedRepository repository=mock(ResearchCompetitorSeedRepository.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final ResearchCompetitorSeedService service=new ResearchCompetitorSeedService(repository,mapper);

    @Test void emptySeedsAreAllowedButReturnAnExplicitWarning(){
        when(repository.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(7L)).thenReturn(List.of());
        var view=service.current(7L);
        assertThat(view.seeds()).isEmpty(); assertThat(view.warning()).isNotBlank();
    }

    @Test void duplicateNamesAndMoreThanEightAreRejected(){
        when(repository.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(7L)).thenReturn(List.of());
        var duplicate=mapper.readTree("[{\"name\":\"A\",\"reason\":\"r1\"},{\"name\":\"A\",\"reason\":\"r2\"}]");
        assertThatThrownBy(()->service.replace(7L,1L,duplicate)).isInstanceOf(RuntimeException.class);
        var many=mapper.createArrayNode();for(int i=0;i<9;i++)many.addObject().put("name","N"+i).put("reason","R"+i);
        assertThatThrownBy(()->service.replace(7L,1L,many)).isInstanceOf(RuntimeException.class);
    }

    @Test void conceptBlockPreservesOrderReasonAndOptionalOperator(){
        var first=ResearchCompetitorSeed.create("s1",7L,1,"공비서","노쇼 방지","공비서 주식회사",1L);
        var second=ResearchCompetitorSeed.create("s2",7L,2,"수기 장부","현재 대안","",1L);
        when(repository.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(7L)).thenReturn(List.of(first,second));
        var block=service.conceptBlock(7L);
        assertThat(block.path("seeds").path(0).path("이름").asText()).isEqualTo("공비서");
        assertThat(block.path("seeds").path(1).path("운영사").isNull()).isTrue();
    }
}
