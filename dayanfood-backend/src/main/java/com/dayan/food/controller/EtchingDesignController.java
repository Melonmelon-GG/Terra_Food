package com.dayan.food.controller;

import com.dayan.food.entity.dto.EtchingDesignDTO;
import com.dayan.food.entity.vo.EtchingDesignVO;
import com.dayan.food.service.EtchingDesignService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/etchings")
public class EtchingDesignController {
    private final EtchingDesignService etchingDesignService;
    public EtchingDesignController(EtchingDesignService etchingDesignService) { this.etchingDesignService = etchingDesignService; }

    @GetMapping("/me") public List<EtchingDesignVO> listMine(Authentication auth) { return etchingDesignService.listMine(auth.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EtchingDesignVO create(@Valid @RequestBody EtchingDesignDTO request, Authentication auth) { return etchingDesignService.create(auth.getName(), request); }
    @PutMapping("/{id}") public EtchingDesignVO update(@PathVariable Long id, @Valid @RequestBody EtchingDesignDTO request, Authentication auth) { return etchingDesignService.update(auth.getName(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { etchingDesignService.delete(auth.getName(), id); }
    @PutMapping("/{id}/selection") public EtchingDesignVO select(@PathVariable Long id, Authentication auth) { return etchingDesignService.select(auth.getName(), id); }
}
