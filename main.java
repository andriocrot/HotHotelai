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
            this.scoreBand = scoreBand;
            this.atBlock = atBlock;
            this.computedHash = computedHash;
            this.valid = valid;
        }
        public String getPropertyId() { return propertyId; }
        public String getReviewHash() { return reviewHash; }
        public int getScoreBand() { return scoreBand; }
        public long getAtBlock() { return atBlock; }
        public String getComputedHash() { return computedHash; }
        public boolean isValid() { return valid; }
    }

    public static final class GuideRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String guideId;
        private final List<String> segmentHashes;
        private final long createdAt;
        private final String createdBy;

        public GuideRecord(String guideId, long createdAt, String createdBy) {
            this.guideId = guideId;
            this.segmentHashes = new ArrayList<>();
            this.createdAt = createdAt;
            this.createdBy = createdBy;
        }
        public String getGuideId() { return guideId; }
        public List<String> getSegmentHashes() { return segmentHashes; }
        public long getCreatedAt() { return createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void addSegment(String contentHash) { segmentHashes.add(contentHash); }
    }

    // -------------------------------------------------------------------------
    // SERVICE LAYER
    // -------------------------------------------------------------------------

    public static final class PropertyService {
        private final Map<String, PropertyRecord> properties = new LinkedHashMap<>();
        private final Map<String, List<ReviewRecord>> reviewsByProperty = new LinkedHashMap<>();
        private final List<ComparisonSnapshot> comparisons = new ArrayList<>();
        private final Map<String, GuideRecord> guides = new LinkedHashMap<>();
        private final Path basePath;

        public PropertyService() {
            Path userHome = Paths.get(System.getProperty("user.home"));
            this.basePath = userHome.resolve(CONFIG_DIR);
            try {
                Files.createDirectories(basePath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public void addProperty(PropertyRecord p) {
            if (properties.size() >= MAX_PROPERTIES) throw new IllegalStateException("Max properties reached");
            properties.put(p.getPropertyId(), p);
            reviewsByProperty.putIfAbsent(p.getPropertyId(), new ArrayList<>());
        }

        public Optional<PropertyRecord> getProperty(String propertyId) {
            return Optional.ofNullable(properties.get(propertyId));
        }

        public List<PropertyRecord> getAllProperties() {
            return new ArrayList<>(properties.values());
        }

        public List<PropertyRecord> getPropertiesByRegion(String regionHash) {
            return properties.values().stream()
                .filter(p -> regionHash.equals(p.getRegionHash()))
                .collect(Collectors.toList());
        }

        public void addReview(String propertyId, ReviewRecord r) {
            PropertyRecord p = properties.get(propertyId);
            if (p == null) throw new IllegalArgumentException("Property not found: " + propertyId);
            List<ReviewRecord> list = reviewsByProperty.get(propertyId);
            if (list.size() >= MAX_REVIEWS_PER_PROPERTY) throw new IllegalStateException("Max reviews per property");
            list.add(r);
            p.setReviewCount(list.size());
            p.setCurrentScoreBand(r.getScoreBand());
        }

        public List<ReviewRecord> getReviews(String propertyId) {
            return new ArrayList<>(reviewsByProperty.getOrDefault(propertyId, Collections.emptyList()));
        }

        public double getAverageScoreBand(String propertyId) {
            List<ReviewRecord> list = reviewsByProperty.getOrDefault(propertyId, Collections.emptyList());
            if (list.isEmpty()) return 0.0;
            return list.stream().mapToInt(ReviewRecord::getScoreBand).average().orElse(0.0);
        }

        public int getMedianScoreBand(String propertyId) {
            List<ReviewRecord> list = reviewsByProperty.getOrDefault(propertyId, Collections.emptyList());
            if (list.isEmpty()) return 0;
            int[] bands = list.stream().mapToInt(ReviewRecord::getScoreBand).sorted().toArray();
            int mid = bands.length / 2;
            return bands.length % 2 == 1 ? bands[mid] : (bands[mid - 1] + bands[mid]) / 2;
        }

        public void addComparison(ComparisonSnapshot c) {
            comparisons.add(c);
        }

        public List<ComparisonSnapshot> getComparisons() {
            return new ArrayList<>(comparisons);
        }

        public Optional<ComparisonSnapshot> getComparison(String leftId, String rightId) {
            return comparisons.stream()
                .filter(x -> (x.getLeftId().equals(leftId) && x.getRightId().equals(rightId))
                    || (x.getLeftId().equals(rightId) && x.getRightId().equals(leftId)))
                .findFirst();
        }

        public List<PropertyRecord> getTopByScoreBand(String regionHash, int limit) {
            return getPropertiesByRegion(regionHash).stream()
                .sorted((a, b) -> Integer.compare(b.getCurrentScoreBand(), a.getCurrentScoreBand()))
                .limit(limit)
                .collect(Collectors.toList());
        }

        public Set<String> getAllRegions() {
            return properties.values().stream().map(PropertyRecord::getRegionHash).collect(Collectors.toSet());
        }

        public List<RegionStats> getRegionStats() {
            Map<String, List<PropertyRecord>> byRegion = new LinkedHashMap<>();
            for (PropertyRecord p : properties.values()) {
                byRegion.computeIfAbsent(p.getRegionHash(), k -> new ArrayList<>()).add(p);
            }
            List<RegionStats> out = new ArrayList<>();
            for (Map.Entry<String, List<PropertyRecord>> e : byRegion.entrySet()) {
                List<PropertyRecord> list = e.getValue();
                int totalReviews = list.stream().mapToInt(PropertyRecord::getReviewCount).sum();
                double avg = list.stream().mapToInt(PropertyRecord::getCurrentScoreBand).average().orElse(0.0);
                out.add(new RegionStats(e.getKey(), list.size(), avg, totalReviews));
            }
            return out;
        }

        public void addGuide(GuideRecord g) {
            guides.put(g.getGuideId(), g);
        }

        public Optional<GuideRecord> getGuide(String guideId) {
            return Optional.ofNullable(guides.get(guideId));
        }

        public List<GuideRecord> getAllGuides() {
            return new ArrayList<>(guides.values());
        }

        public void addGuideSegment(String guideId, String contentHash) {
            GuideRecord g = guides.get(guideId);
            if (g != null) g.addSegment(contentHash);
        }

        public List<LatticeVerificationResult> verifyLatticeBatch(String propertyId) {
            List<ReviewRecord> list = reviewsByProperty.getOrDefault(propertyId, Collections.emptyList());
            List<LatticeVerificationResult> results = new ArrayList<>();
            for (ReviewRecord r : list) {
                String computed = computeLatticeHash(propertyId, r.getReviewHash(), r.getScoreBand(), r.getBlockAnchored());
                results.add(new LatticeVerificationResult(propertyId, r.getReviewHash(), r.getScoreBand(), r.getBlockAnchored(), computed, true));
            }
            return results;
        }

        public void exportCsv(Path path) throws IOException {
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                w.write("propertyId,regionHash,listedBy,blockListed,scoreBand,reviewCount,frozen\n");
                for (PropertyRecord p : properties.values()) {
                    w.write(String.format("%s,%s,%s,%d,%d,%d,%s\n",
                        p.getPropertyId(), p.getRegionHash(), p.getListedBy(), p.getBlockListed(),
                        p.getCurrentScoreBand(), p.getReviewCount(), p.isFrozen()));
                }
            }
        }

        public int getTotalReviewCount() {
            return reviewsByProperty.values().stream().mapToInt(List::size).sum();
        }

        public void clearAll() {
            properties.clear();
            reviewsByProperty.clear();
            comparisons.clear();
            guides.clear();
        }

        public String computeLatticeHash(String propertyId, String reviewHash, int scoreBand, long atBlock) {
            String salt = "Hotelia.HTL_LATTICE_SALT.v2";
            String input = salt + propertyId + reviewHash + scoreBand + atBlock;
            return String.valueOf(input.hashCode());
        }

        public void save() throws IOException {
            try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(basePath.resolve(DATA_FILE).toFile()))) {
                oos.writeObject(new ArrayList<>(properties.values()));
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(basePath.resolve(REVIEWS_FILE).toFile()))) {
                oos.writeObject(new HashMap<>(reviewsByProperty));
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(basePath.resolve(COMPARISONS_FILE).toFile()))) {
                oos.writeObject(new ArrayList<>(comparisons));
            }
            Path guidesPath = basePath.resolve("guides.dat");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(guidesPath.toFile()))) {
                oos.writeObject(new HashMap<>(guides));
            }
        }

        @SuppressWarnings("unchecked")
        public void load() throws IOException, ClassNotFoundException {
            Path dataPath = basePath.resolve(DATA_FILE);
            if (Files.exists(dataPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataPath.toFile()))) {
                    List<PropertyRecord> list = (List<PropertyRecord>) ois.readObject();
                    properties.clear();
                    for (PropertyRecord p : list) properties.put(p.getPropertyId(), p);
                }
            }
            Path reviewsPath = basePath.resolve(REVIEWS_FILE);
            if (Files.exists(reviewsPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(reviewsPath.toFile()))) {
                    reviewsByProperty.clear();
                    reviewsByProperty.putAll((Map<String, List<ReviewRecord>>) ois.readObject());
                }
            }
            Path compPath = basePath.resolve(COMPARISONS_FILE);
            if (Files.exists(compPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(compPath.toFile()))) {
                    comparisons.clear();
                    comparisons.addAll((List<ComparisonSnapshot>) ois.readObject());
                }
            }
            Path guidesPath = basePath.resolve("guides.dat");
            if (Files.exists(guidesPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(guidesPath.toFile()))) {
                    guides.clear();
                    guides.putAll((Map<String, GuideRecord>) ois.readObject());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // MAIN FRAME & UI
    // -------------------------------------------------------------------------

    private final PropertyService service;
    private JFrame frame;
    private JTable propertyTable;
    private DefaultTableModel propertyModel;
    private JTable reviewTable;
    private DefaultTableModel reviewModel;
    private JComboBox<String> regionFilter;
    private JTextField searchField;
    private JLabel statusLabel;

    public HotHotelai() {
        this.service = new PropertyService();
        try {
            service.load();
        } catch (Exception e) {
            System.err.println("Load warning: " + e.getMessage());
        }
    }

    private void buildUI() {
        frame = new JFrame(APP_TITLE + " — Hotel comparison & AI review checker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 720);
        frame.setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        searchField = new JTextField(24);
        searchField.setToolTipText("Search property ID or region");
        regionFilter = new JComboBox<>(new String[] { "<All regions>", "region-eu-1", "region-us-1", "region-asia-1" });
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshPropertyTable());
        JButton addPropBtn = new JButton("Add property");
        addPropBtn.addActionListener(e -> showAddPropertyDialog());
        JButton addReviewBtn = new JButton("Add review");
        addReviewBtn.addActionListener(e -> showAddReviewDialog());
        JButton compareBtn = new JButton("Compare");
        compareBtn.addActionListener(e -> showCompareDialog());
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> saveData());
        topBar.add(new JLabel("Search:"));
        topBar.add(searchField);
        topBar.add(regionFilter);
        topBar.add(refreshBtn);
        topBar.add(addPropBtn);
        topBar.add(addReviewBtn);
        topBar.add(compareBtn);
        topBar.add(saveBtn);
        topBar.add(createSearchSelectButton());

        propertyModel = new DefaultTableModel(
            new String[] { "Property ID", "Region", "Listed by", "Score band", "Reviews", "Frozen" }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        propertyTable = new JTable(propertyModel);
        propertyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        propertyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onPropertySelection();
        });
        JScrollPane propScroll = new JScrollPane(propertyTable);
        propScroll.setPreferredSize(new Dimension(0, 220));

        reviewModel = new DefaultTableModel(
            new String[] { "Review hash", "Score band", "Block", "Anchored by" }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        reviewTable = new JTable(reviewModel);
        JScrollPane reviewScroll = new JScrollPane(reviewTable);
        reviewScroll.setPreferredSize(new Dimension(0, 180));

        statusLabel = new JLabel("Ready. Properties: " + service.getAllProperties().size());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JTabbedPane tabs = new JTabbedPane();
        JPanel propsPanel = new JPanel(new BorderLayout(8, 8));
        propsPanel.add(topBar, BorderLayout.NORTH);
        propsPanel.add(propScroll, BorderLayout.CENTER);
        propsPanel.add(reviewScroll, BorderLayout.SOUTH);
        propsPanel.add(statusLabel, BorderLayout.PAGE_END);
        tabs.addTab("Properties", propsPanel);

