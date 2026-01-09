package com.nsdev.bidflow.domain.repository;

import com.nsdev.bidflow.domain.model.Auction;
import com.nsdev.bidflow.domain.model.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    List<Auction> findByStatus(AuctionStatus status);
}