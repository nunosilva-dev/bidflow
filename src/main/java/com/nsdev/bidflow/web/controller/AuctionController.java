package com.nsdev.bidflow.web.controller;

import com.nsdev.bidflow.domain.dto.CreateAuctionRequest;
import com.nsdev.bidflow.domain.service.AuctionReadService;
import com.nsdev.bidflow.domain.service.AuctionWriteService;
import com.nsdev.bidflow.web.dto.AuctionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionReadService readService;
    private final AuctionWriteService writeService;

    @GetMapping
    public ResponseEntity<List<AuctionResponse>> getAllOpenAuctions() {
        var auctions = readService.getAllOpenAuctions();

        var response = auctions.stream()
                .map(AuctionResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> getAuctionById(@PathVariable Long id) {
        var auction = readService.getAuctionById(id);
        return ResponseEntity.ok(AuctionResponse.fromEntity(auction));
    }

    @PostMapping
    public ResponseEntity<AuctionResponse> createAuction(@Valid @RequestBody CreateAuctionRequest request) {
        var auction = writeService.createAuction(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuctionResponse.fromEntity(auction));
    }
}