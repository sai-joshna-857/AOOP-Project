import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Save this file as OrderLifecycleDemo.java
public class OrderLifecycleDemo {

    enum OrderStatus {
        DRAFT, PLACED, PAID, SHIPPED, DELIVERED, CANCELLED
    }

    // A simple immutable order line value.
    static final class OrderLine {
        private final String sku;
        private final String productName;
        private final BigDecimal unitPrice;
        private int quantity;

        OrderLine(String sku, String productName,
                  BigDecimal unitPrice, int quantity) {
            if (sku == null || sku.trim().isEmpty()) {
                throw new IllegalArgumentException("SKU cannot be empty.");
            }
            if (productName == null || productName.trim().isEmpty()) {
                throw new IllegalArgumentException("Product name cannot be empty.");
            }
            if (unitPrice == null || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("Unit price cannot be negative.");
            }
            if (quantity < 1) {
                throw new IllegalArgumentException("Quantity must be at least 1.");
            }

            this.sku = sku;
            this.productName = productName;
            this.unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
            this.quantity = quantity;
        }

        void increaseQuantity(int amount) {
            if (amount < 1) {
                throw new IllegalArgumentException("Quantity must be positive.");
            }
            quantity = Math.addExact(quantity, amount);
        }

        BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.UNNECESSARY);
        }

        String getSku() {
            return sku;
        }

        void display() {
            System.out.println(productName + " (" + sku + ")"
                    + " | Qty: " + quantity
                    + " | Unit price: " + unitPrice
                    + " | Line total: " + lineTotal());
        }
    }

    // Shipment details are created only when a paid order is shipped.
    static final class Shipment {
        private final String trackingNumber;
        private final Instant shippedAt;

        Shipment(String trackingNumber, Instant shippedAt) {
            if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Tracking number is required.");
            }
            this.trackingNumber = trackingNumber;
            this.shippedAt = Objects.requireNonNull(shippedAt);
        }

        @Override
        public String toString() {
            return "Tracking number: " + trackingNumber
                    + ", shipped at: " + shippedAt;
        }
    }

    // Entity / aggregate root: one Order object controls its own invariants.
    static final class Order {
        private final String orderId;
        private final Map<String, OrderLine> lines = new LinkedHashMap<>();
        private OrderStatus status = OrderStatus.DRAFT;
        private Shipment shipment;

        Order(String orderId) {
            if (orderId == null || orderId.trim().isEmpty()) {
                throw new IllegalArgumentException("Order ID cannot be empty.");
            }
            this.orderId = orderId;
        }

        // Lines can be changed only while the order is in DRAFT.
        public void addLine(String sku, String productName,
                            BigDecimal unitPrice, int quantity) {
            requireStatus(OrderStatus.DRAFT);

            OrderLine existing = lines.get(sku);
            if (existing == null) {
                lines.put(sku,
                        new OrderLine(sku, productName, unitPrice, quantity));
            } else {
                // Same SKU is merged into one line instead of creating duplicates.
                existing.increaseQuantity(quantity);
            }
        }

        public void removeLine(String sku) {
            requireStatus(OrderStatus.DRAFT);
            if (lines.remove(sku) == null) {
                throw new IllegalArgumentException("Product SKU not found: " + sku);
            }
        }

        // Total is calculated from lines; it is not stored separately.
        public BigDecimal total() {
            BigDecimal result = BigDecimal.ZERO.setScale(2);
            for (OrderLine line : lines.values()) {
                result = result.add(line.lineTotal());
            }
            return result.setScale(2, RoundingMode.UNNECESSARY);
        }

        public void place() {
            requireStatus(OrderStatus.DRAFT);
            if (lines.isEmpty()) {
                throw new IllegalStateException(
                        "An order must contain at least one line before placing.");
            }
            status = OrderStatus.PLACED;
        }

        public void markPaid() {
            requireStatus(OrderStatus.PLACED);
            status = OrderStatus.PAID;
        }

        // This follows the guarded ship method in the case study.
        public void ship(String trackingNumber, Instant shippedAt) {
            requireStatus(OrderStatus.PAID);
            Shipment newShipment = new Shipment(trackingNumber, shippedAt);
            shipment = newShipment;
            status = OrderStatus.SHIPPED;
        }

        public void deliver() {
            requireStatus(OrderStatus.SHIPPED);
            status = OrderStatus.DELIVERED;
        }

        public void cancel() {
            if (status != OrderStatus.DRAFT
                    && status != OrderStatus.PLACED
                    && status != OrderStatus.PAID) {
                throw new IllegalStateException(
                        "Only Draft, Placed or Paid orders can be cancelled.");
            }
            status = OrderStatus.CANCELLED;
        }

        private void requireStatus(OrderStatus required) {
            if (status != required) {
                throw new IllegalStateException(
                        "Invalid operation. Current status is " + status
                                + "; required status is " + required + ".");
            }
        }

        public OrderStatus getStatus() {
            return status;
        }

        public String getOrderId() {
            return orderId;
        }

        public void displayOrder() {
            System.out.println("\nOrder ID: " + orderId);
            System.out.println("Status: " + status);
            System.out.println("---------------------------------------------");

            if (lines.isEmpty()) {
                System.out.println("No order lines.");
            } else {
                for (OrderLine line : lines.values()) {
                    line.display();
                }
            }

            System.out.println("---------------------------------------------");
            System.out.println("Order Total: " + total());

            if (shipment != null) {
                System.out.println("Shipment: " + shipment);
            }
        }
    }

    // Separate aggregate representing the customer's available credit.
    static final class CustomerCreditAccount {
        private final String customerId;
        private final BigDecimal creditLimit;
        private BigDecimal reservedCredit = BigDecimal.ZERO.setScale(2);

        CustomerCreditAccount(String customerId, BigDecimal creditLimit) {
            if (customerId == null || customerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Customer ID is required.");
            }
            if (creditLimit == null || creditLimit.signum() < 0) {
                throw new IllegalArgumentException("Credit limit cannot be negative.");
            }
            this.customerId = customerId;
            this.creditLimit = creditLimit.setScale(2, RoundingMode.UNNECESSARY);
        }

        // Synchronized so simultaneous requests cannot reserve the same credit.
        public synchronized void reserve(BigDecimal amount) {
            BigDecimal newReserved = reservedCredit.add(amount);
            if (newReserved.compareTo(creditLimit) > 0) {
                throw new IllegalStateException(
                        "Customer credit limit exceeded. Available credit: "
                                + availableCredit());
            }
            reservedCredit = newReserved;
        }

        public synchronized void release(BigDecimal amount) {
            if (amount.signum() < 0 || amount.compareTo(reservedCredit) > 0) {
                throw new IllegalArgumentException("Invalid credit release amount.");
            }
            reservedCredit = reservedCredit.subtract(amount);
        }

        public synchronized BigDecimal availableCredit() {
            return creditLimit.subtract(reservedCredit);
        }

        public String getCustomerId() {
            return customerId;
        }
    }

    // Application/domain workflow coordinates the Order and Credit aggregates.
    static final class OrderApplicationService {

        public void placeOrder(Order order, CustomerCreditAccount account) {
            Objects.requireNonNull(order);
            Objects.requireNonNull(account);

            BigDecimal amount = order.total();

            // Reserve credit first. If placing the order fails, undo the reservation.
            account.reserve(amount);
            try {
                order.place();
            } catch (RuntimeException ex) {
                account.release(amount);
                throw ex;
            }
        }

        public void cancelOrder(Order order, CustomerCreditAccount account) {
            Objects.requireNonNull(order);
            Objects.requireNonNull(account);

            BigDecimal amount = order.total();
            order.cancel();
            account.release(amount);
        }
    }

    public static void main(String[] args) {
        OrderApplicationService service = new OrderApplicationService();

        CustomerCreditAccount customer =
                new CustomerCreditAccount("CUST-101", new BigDecimal("2000.00"));

        Order order = new Order("ORD-1001");

        try {
            System.out.println("=== ADDING PRODUCTS ===");
            order.addLine("P101", "Laptop",
                    new BigDecimal("1200.00"), 1);
            order.addLine("P102", "Wireless Mouse",
                    new BigDecimal("25.50"), 2);

            // Adding the same SKU increases quantity; it does not create a duplicate line.
            order.addLine("P102", "Wireless Mouse",
                    new BigDecimal("25.50"), 1);

            order.displayOrder();

            System.out.println("\n=== PLACING ORDER ===");
            service.placeOrder(order, customer);
            System.out.println("Order placed successfully.");
            System.out.println("Available customer credit: "
                    + customer.availableCredit());
            order.displayOrder();

            System.out.println("\n=== PAYMENT ===");
            order.markPaid();
            System.out.println("Payment successful. Status: " + order.getStatus());

            System.out.println("\n=== SHIPPING ===");
            order.ship("TRACK-ABC-123", Instant.now());
            System.out.println("Order shipped successfully.");
            order.displayOrder();

            System.out.println("\n=== DELIVERY ===");
            order.deliver();
            System.out.println("Order delivered. Status: " + order.getStatus());

            // Demonstrate that a delivered order cannot be cancelled.
            System.out.println("\n=== INVALID TRANSITION TEST ===");
            try {
                order.cancel();
            } catch (IllegalStateException ex) {
                System.out.println("Correctly rejected: " + ex.getMessage());
            }

            // Demonstrate the customer credit-limit rule with another order.
            System.out.println("\n=== CREDIT LIMIT TEST ===");
            Order expensiveOrder = new Order("ORD-1002");
            expensiveOrder.addLine("P200", "Expensive Server",
                    new BigDecimal("2500.00"), 1);

            try {
                service.placeOrder(expensiveOrder, customer);
            } catch (IllegalStateException ex) {
                System.out.println("Correctly rejected: " + ex.getMessage());
                System.out.println("Second order status: "
                        + expensiveOrder.getStatus());
            }

        } catch (RuntimeException ex) {
            System.out.println("Operation failed: " + ex.getMessage());
        }
    }
}
