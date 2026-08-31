package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.CacheConstants;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class FoodPhotoHistoryService {
    private static final int HISTORY_DAYS = 90;
    private static final int MAX_HISTORY_FOODS = 100;

    private final IntakeRepository intakeRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = CacheConstants.FOOD_PHOTO_HISTORY, key = "#userId")
    @Transactional(readOnly = true)
    public List<String> getTopFoodIds(Long userId) {
        return intakeRepository.findFrequentRecentFoodIds(
                userId,
                LocalDate.now().minusDays(HISTORY_DAYS),
                PageRequest.of(0, MAX_HISTORY_FOODS)
        );
    }

    public void evictAfterCommit(Long userId) {
        Runnable eviction = () -> {
            Cache cache = cacheManager.getCache(CacheConstants.FOOD_PHOTO_HISTORY);
            if (cache != null) {
                cache.evict(userId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eviction.run();
                        }
                    });
        } else {
            eviction.run();
        }
    }

}
