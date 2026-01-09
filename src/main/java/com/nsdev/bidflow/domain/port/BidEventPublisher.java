package com.nsdev.bidflow.domain.port;

import com.nsdev.bidflow.domain.model.Bid;

public interface BidEventPublisher {
    void publishNewBid(Bid bid);
}