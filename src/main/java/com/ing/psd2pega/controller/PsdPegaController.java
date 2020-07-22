package com.ing.psd2pega.controller;

import com.ing.psd2pega.service.PsdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PsdPegaController {
    @Autowired
    PsdService psdService;
    @GetMapping("/getPsdData")
    public String getPsd2Data (@RequestParam String fkn, @RequestParam String pin) {

        String responce =  psdService.getPSDinfo(fkn, pin);
        return responce;
    }
}
