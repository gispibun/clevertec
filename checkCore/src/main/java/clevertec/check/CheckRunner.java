package clevertec.check;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Product {
    private int id;
    private String description;
    private BigDecimal price;
    private int quantityInStock;
    private boolean wholesaleProduct;

    public Product(int id, String description, double price, int quantityInStock, boolean wholesaleProduct) {
        this.id = id;
        this.description = description;
        this.price = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
        this.quantityInStock = quantityInStock;
        this.wholesaleProduct = wholesaleProduct;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantityInStock() {
        return quantityInStock;
    }

    public boolean isWholesaleProduct() {
        return wholesaleProduct;
    }
}

class DiscountCard {
    private static final BigDecimal DEFAULT_DISCOUNT_RATE = BigDecimal.valueOf(2.00).setScale(2, RoundingMode.HALF_UP);

    private int number;
    private BigDecimal discountRate;

    public DiscountCard(int number, double discountRate) {
        this.number = number;
        this.discountRate = BigDecimal.valueOf(discountRate).setScale(2, RoundingMode.HALF_UP);
    }

    public int getNumber() {
        return number;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }

    public static BigDecimal getDefaultDiscountRate() {
        return DEFAULT_DISCOUNT_RATE;
    }
}

public class CheckRunner {
    private List<Product> products;
    private List<DiscountCard> discountCards;

    public CheckRunner(String productFilePath, String discountCardFilePath) throws IOException {
        products = new ArrayList<>();
        discountCards = new ArrayList<>();

        try (Reader reader1 = new FileReader(productFilePath)) {
            Iterable<CSVRecord> records1 = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withDelimiter(';')
                    .withTrim()
                    .parse(reader1);
            for (CSVRecord record : records1) {
                Product product = new Product(
                        Integer.parseInt(record.get("id")),
                        record.get("description"),
                        Double.parseDouble(record.get("price")),
                        Integer.parseInt(record.get("quantity_in_stock")),
                        Boolean.parseBoolean(record.get("wholesale_product"))
                );
                products.add(product);
            }
        }

        try (Reader reader2 = new FileReader(discountCardFilePath)) {
            Iterable<CSVRecord> records2 = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withDelimiter(';')
                    .withTrim()
                    .parse(reader2);
            for (CSVRecord record : records2) {
                DiscountCard card = new DiscountCard(
                        Integer.parseInt(record.get("number")),
                        Double.parseDouble(record.get("amount"))
                );
                discountCards.add(card);
            }
        }
    }

    public List<Product> queryProducts(Map<Integer, Integer> productQuantities) throws IllegalArgumentException {
        List<Product> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : productQuantities.entrySet()) {
            int productId = entry.getKey();
            int quantity = entry.getValue();
            boolean found = false;
            for (Product product : products) {
                if (product.getId() == productId) {
                    found = true;
                    for (int i = 0; i < quantity; i++) {
                        result.add(product);
                    }
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Product with ID " + productId + " not found.");
            }
        }
        return result;
    }

    public DiscountCard queryDiscountCard(int cardNumber) {
        for (DiscountCard card : discountCards) {
            if (card.getNumber() == cardNumber) {
                return card;
            }
        }
        // If card is not found, return default discount card with 2% discount
        return new DiscountCard(cardNumber, DiscountCard.getDefaultDiscountRate().doubleValue());
    }

    public void generateReceipt(List<Product> products, DiscountCard discountCard, double balanceDebitCard, String resultFilePath) throws IOException {
        BigDecimal totalSumWithoutDiscounts = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Map<Integer, Integer> productCounts = new HashMap<>();

        for (Product product : products) {
            int productId = product.getId();
            productCounts.put(productId, productCounts.getOrDefault(productId, 0) + 1);
        }

        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(resultFilePath), CSVFormat.DEFAULT.withDelimiter(';'))) {
            printer.printRecord("Date;", date);
            printer.printRecord("Time;", time);
            printer.println();
            printer.printRecord("QTY", "DESCRIPTION", "PRICE", "DISCOUNT", "TOTAL");

            for (Map.Entry<Integer, Integer> entry : productCounts.entrySet()) {
                int productId = entry.getKey();
                int quantity = entry.getValue();
                Product product = findProductById(productId);

                if (product == null) {
                    throw new IllegalArgumentException("Product with ID " + productId + " not found.");
                }

                BigDecimal productTotalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));
                BigDecimal productDiscount = BigDecimal.ZERO;
                String discountInfo = "";

                totalSumWithoutDiscounts = totalSumWithoutDiscounts.add(productTotalPrice);

                if (product.isWholesaleProduct() && quantity >= 5) {
                    productDiscount = productTotalPrice.multiply(BigDecimal.valueOf(0.10));
                    discountInfo = "10% wholesale";
                } else if (discountCard != null) {
                    BigDecimal cardDiscount = productTotalPrice.multiply(discountCard.getDiscountRate().divide(BigDecimal.valueOf(100)));
                    productDiscount = productDiscount.add(cardDiscount);
                    discountInfo = discountCard.getDiscountRate() + "% card discount";
                }

                totalDiscount = totalDiscount.add(productDiscount);

                printer.printRecord(
                        quantity,
                        product.getDescription(),
                        product.getPrice(),
                        productDiscount.setScale(2, RoundingMode.HALF_UP),
                        productTotalPrice.subtract(productDiscount).setScale(2, RoundingMode.HALF_UP)
                );
            }

            BigDecimal totalWithDiscount = totalSumWithoutDiscounts.subtract(totalDiscount);

            printer.println();
            printer.printRecord("DISCOUNT CARD;", discountCard.getNumber());
            printer.printRecord("DISCOUNT PERCENTAGE;", discountCard.getDiscountRate() + "%");
            printer.println();
            printer.printRecord("TOTAL PRICE;", totalSumWithoutDiscounts.setScale(2, RoundingMode.HALF_UP));
            printer.printRecord("TOTAL DISCOUNT;", totalDiscount.setScale(2, RoundingMode.HALF_UP));
            printer.printRecord("TOTAL WITH DISCOUNT;", totalWithDiscount.setScale(2, RoundingMode.HALF_UP));
        }

        // Вывод в консоль для проверки
        System.out.println("Date: " + date);
        System.out.println("Time: " + time);
        System.out.println();
        System.out.printf("%-5s %-30s %-10s %-10s %-10s\n", "QTY", "DESCRIPTION", "PRICE", "DISCOUNT", "TOTAL");

        for (Map.Entry<Integer, Integer> entry : productCounts.entrySet()) {
            int productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = findProductById(productId);

            if (product == null) {
                throw new IllegalArgumentException("Product with ID " + productId + " not found.");
            }

            BigDecimal productTotalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal productDiscount = BigDecimal.ZERO;
            String discountInfo = "";

            if (product.isWholesaleProduct() && quantity >= 5) {
                productDiscount = productTotalPrice.multiply(BigDecimal.valueOf(0.10));
                discountInfo = "10% wholesale";
            } else if (discountCard != null) {
                BigDecimal cardDiscount = productTotalPrice.multiply(discountCard.getDiscountRate().divide(BigDecimal.valueOf(100)));
                productDiscount = productDiscount.add(cardDiscount);
                discountInfo = discountCard.getDiscountRate() + "% card discount";
            }

            System.out.printf("%-5d %-30s %-10.2f %-10.2f %-10.2f\n",
                    quantity,
                    product.getDescription(),
                    product.getPrice(),
                    productDiscount.setScale(2, RoundingMode.HALF_UP),
                    productTotalPrice.subtract(productDiscount).setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal totalWithDiscount = totalSumWithoutDiscounts.subtract(totalDiscount);

        System.out.println();
        System.out.printf("TOTAL PRICE: %-10.2f\n", totalSumWithoutDiscounts.setScale(2, RoundingMode.HALF_UP));
        System.out.printf("TOTAL DISCOUNT: %-10.2f\n", totalDiscount.setScale(2, RoundingMode.HALF_UP));
        System.out.printf("TOTAL WITH DISCOUNT: %-10.2f\n", totalWithDiscount.setScale(2, RoundingMode.HALF_UP));
    }

    private Product findProductById(int productId) {
        for (Product product : products) {
            if (product.getId() == productId) {
                return product;
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        String productFilePath = "./src/main/resources/products.csv";
        String discountCardFilePath = "./src/main/resources/discountCards.csv";
        String resultFilePath = "receipt.csv";

        Map<Integer, Integer> productQuantities = new HashMap<>();
        productQuantities.put(1, 4);
        productQuantities.put(2, 5);

        CheckRunner checkRunner = new CheckRunner(productFilePath, discountCardFilePath);
        List<Product> products = checkRunner.queryProducts(productQuantities);
        DiscountCard discountCard = checkRunner.queryDiscountCard(1111);

        checkRunner.generateReceipt(products, discountCard, 0, resultFilePath);
    }
}
