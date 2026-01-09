package com.nsdev.bidflow.domain.service;

import com.nsdev.bidflow.domain.exception.AuctionNotFoundException;
import com.nsdev.bidflow.domain.model.Auction;
import com.nsdev.bidflow.domain.model.AuctionStatus;
import com.nsdev.bidflow.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionReadService {

    private final AuctionRepository auctionRepository;

    public List<Auction> getAllOpenAuctions() {
        return auctionRepository.findByStatus(AuctionStatus.OPEN);
    }

    public Auction getAuctionById(Long id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionNotFoundException(id));
    }
}