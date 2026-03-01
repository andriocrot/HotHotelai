/*
 * HotHotelai — Hotel comparison and guide driven by AI review checker.
 * Single-file application: data models, services, and UI for property comparison and review verification.
 */

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.*;
import java.time.*;
import java.time.format.*;

public final class HotHotelai {

    private static final String APP_TITLE = "HotHotelai";
    private static final String CONFIG_DIR = ".hotelai";
    private static final String DATA_FILE = "properties.dat";
    private static final String REVIEWS_FILE = "reviews.dat";
    private static final String COMPARISONS_FILE = "comparisons.dat";
    private static final int MAX_PROPERTIES = 88000;
    private static final int MAX_REVIEWS_PER_PROPERTY = 500;
    private static final int SCORE_BAND_MAX = 10;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String TRAIT_AMENITY = "Hotelia.TRAIT.AMENITY";
    private static final String TRAIT_PRICE_TIER = "Hotelia.TRAIT.PRICE_TIER";
    private static final String TRAIT_STAR_RATING = "Hotelia.TRAIT.STAR_RATING";
    private static final String TRAIT_CHAIN_ID = "Hotelia.TRAIT.CHAIN_ID";
    private static final String TRAIT_LOCALE = "Hotelia.TRAIT.LOCALE";
    private static final String TRAIT_AI_SUMMARY = "Hotelia.TRAIT.AI_SUMMARY";
    private static final int MAX_BATCH_LIST = 75;
    private static final int MAX_BATCH_REVIEW = 50;
    private static final int MAX_GUIDE_SEGMENTS = 200;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String LATTICE_SALT = "Hotelia.HTL_LATTICE_SALT.v2";
    private static final String GUIDE_ANCHOR = "Hotelia.HTL_GUIDE_ANCHOR";
    private static final String[] DEFAULT_REGIONS = { "region-eu-1", "region-us-1", "region-asia-1", "region-eu-2", "region-sa-1" };

    // -------------------------------------------------------------------------
    // DATA MODELS
    // -------------------------------------------------------------------------

    public static final class PropertyRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String propertyId;
        private final String regionHash;
        private final String listedBy;
        private final long blockListed;
        private boolean frozen;
        private int currentScoreBand;
        private int reviewCount;
        private String traitBundleHash;
        private final Map<String, String> traits;

        public PropertyRecord(String propertyId, String regionHash, String listedBy, long blockListed) {
            this.propertyId = Objects.requireNonNull(propertyId);
            this.regionHash = Objects.requireNonNull(regionHash);
            this.listedBy = Objects.requireNonNull(listedBy);
            this.blockListed = blockListed;
            this.traits = new LinkedHashMap<>();
        }

        public String getPropertyId() { return propertyId; }
        public String getRegionHash() { return regionHash; }
        public String getListedBy() { return listedBy; }
        public long getBlockListed() { return blockListed; }
        public boolean isFrozen() { return frozen; }
        public void setFrozen(boolean frozen) { this.frozen = frozen; }
        public int getCurrentScoreBand() { return currentScoreBand; }
        public void setCurrentScoreBand(int band) { this.currentScoreBand = Math.min(SCORE_BAND_MAX, Math.max(0, band)); }
        public int getReviewCount() { return reviewCount; }
        public void setReviewCount(int count) { this.reviewCount = count; }
        public String getTraitBundleHash() { return traitBundleHash; }
        public void setTraitBundleHash(String h) { this.traitBundleHash = h; }
        public Map<String, String> getTraits() { return traits; }
    }

    public static final class ReviewRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String reviewHash;
        private final int scoreBand;
        private final long blockAnchored;
        private final String anchoredBy;

        public ReviewRecord(String reviewHash, int scoreBand, long blockAnchored, String anchoredBy) {
            this.reviewHash = reviewHash;
            this.scoreBand = Math.min(SCORE_BAND_MAX, Math.max(0, scoreBand));
            this.blockAnchored = blockAnchored;
            this.anchoredBy = anchoredBy;
        }

        public String getReviewHash() { return reviewHash; }
        public int getScoreBand() { return scoreBand; }
        public long getBlockAnchored() { return blockAnchored; }
        public String getAnchoredBy() { return anchoredBy; }
    }

    public static final class ComparisonSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String leftId;
        private final String rightId;
        private final String diffHash;

        public ComparisonSnapshot(String leftId, String rightId, String diffHash) {
            this.leftId = leftId;
            this.rightId = rightId;
            this.diffHash = diffHash;
        }

        public String getLeftId() { return leftId; }
        public String getRightId() { return rightId; }
        public String getDiffHash() { return diffHash; }
    }

    public static final class GuideSegment implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String guideId;
        private final int segmentIndex;
        private final String contentHash;

        public GuideSegment(String guideId, int segmentIndex, String contentHash) {
            this.guideId = guideId;
            this.segmentIndex = segmentIndex;
            this.contentHash = contentHash;
        }

        public String getGuideId() { return guideId; }
        public int getSegmentIndex() { return segmentIndex; }
        public String getContentHash() { return contentHash; }
    }

    public static final class RegionStats implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String regionHash;
        private final int propertyCount;
        private final double avgScoreBand;
        private final int totalReviews;

        public RegionStats(String regionHash, int propertyCount, double avgScoreBand, int totalReviews) {
            this.regionHash = regionHash;
            this.propertyCount = propertyCount;
            this.avgScoreBand = avgScoreBand;
            this.totalReviews = totalReviews;
        }
        public String getRegionHash() { return regionHash; }
        public int getPropertyCount() { return propertyCount; }
        public double getAvgScoreBand() { return avgScoreBand; }
        public int getTotalReviews() { return totalReviews; }
    }

    public static final class LatticeVerificationResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String propertyId;
        private final String reviewHash;
        private final int scoreBand;
        private final long atBlock;
        private final String computedHash;
        private final boolean valid;

        public LatticeVerificationResult(String propertyId, String reviewHash, int scoreBand, long atBlock, String computedHash, boolean valid) {
            this.propertyId = propertyId;
            this.reviewHash = reviewHash;
