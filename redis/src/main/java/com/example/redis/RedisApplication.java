package com.example.redis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Reference;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@EnableCaching
@EnableRedisHttpSession
@Log
public class RedisApplication {


    private static final String topic = "chat";

    @Bean
    CacheManager redisCache(RedisConnectionFactory redisConnectionFactory) {
        return RedisCacheManager
                .builder(redisConnectionFactory)
                .build();
    }


    private ApplicationRunner titledRunner(String title, ApplicationRunner applicationRunner) {
        return args -> {
            log.info(title.toUpperCase() + ":");
            applicationRunner.run(args);
        };
    }


    @Bean
    ApplicationRunner geography(RedisTemplate<String, String> redisTemplate) {
        return titledRunner("geography", args -> {
            GeoOperations<String, String> geoOperations = redisTemplate.opsForGeo();

            geoOperations.add("Sicily", new Point(13.361389, 38.115556), "Arigento");
            geoOperations.add("Sicily", new Point(15.087269, 37.502669), "Catania");
            geoOperations.add("Sicily", new Point(13.583333, 37.316667), "Palermo");


            Circle circle = new Circle(new Point(13.583333, 37.316667),
                    new Distance(100, RedisGeoCommands.DistanceUnit.KILOMETERS));


            GeoResults<RedisGeoCommands.GeoLocation<String>> locationGeoResults = geoOperations.radius("Sicily", circle);

            locationGeoResults.getContent().forEach(geoLocationGeoResult -> log.info(geoLocationGeoResult.toString()));
        });
    }

    @Bean
    ApplicationRunner repositories(LineItemRepository lineItemRepository, OrderRepository orderRepository) {
        return titledRunner("repositories", args -> {

            Long orderId = generateId();

            List<LineItem> lineItems = Arrays.asList(new LineItem(orderId, generateId(), "plunger"), new LineItem(orderId, generateId(), "soup"),
                    new LineItem(orderId, generateId(), "coffemug"));

            lineItems
                    .stream()
                    .map(lineItemRepository::save)
                    .forEach(lineItem -> log.info(lineItem.toString()));


            Order order = new Order(orderId, new Date(), lineItems);

            orderRepository.save(order);


            Collection<Order> found = orderRepository.findByWhen(order.getWhen());

            found.forEach(o -> log.info("found : " + o.toString()));

        });
    }


    @Bean
    ApplicationRunner pubSub(RedisTemplate<String, String> redisTemplate) {
        return titledRunner("publish/subscribe", args -> {
            redisTemplate.convertAndSend(topic, "Hello World  @ " + Instant.now().toString());
        });
    }


    @Bean
    RedisMessageListenerContainer listener(RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer mlc = new RedisMessageListenerContainer();
        mlc.setConnectionFactory(redisConnectionFactory);

        mlc.addMessageListener((message, pattern) -> {
            String str = new String(message.getBody());
            log.info("Message from: '" + topic + "':" + str);
        }, new PatternTopic(topic));
        return mlc;
    }

    private Long generateId() {
        long tmp = new Random().nextLong();
        return Math.max(tmp, tmp * -1);
    }

    private long measure(Runnable runnable) {
        long start = System.currentTimeMillis();
        runnable.run();
        long end = System.currentTimeMillis();
        return end - start;
    }


    @Bean
    ApplicationRunner cache(OrderService orderService) {
        return titledRunner("cache", args -> {

            Runnable measure = () -> orderService.byId(1L);

            log.info("First:" + measure(measure));
            log.info("Second:" + measure(measure));
            log.info("three::" + measure(measure));
        });
    }

    public static void main(String[] args) {
        SpringApplication.run(RedisApplication.class, args);
    }
}


@Service
class OrderService {

    @Cacheable("order-by-id")
    public Order byId(Long id) {
        try {
            Thread.sleep(1000 * 10);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return new Order(id, new Date(), Collections.emptyList());
    }
}


interface OrderRepository extends CrudRepository<Order, Long> {
    Collection<Order> findByWhen(Date when);
}


interface LineItemRepository extends CrudRepository<LineItem, Long> {
}


@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("orders")
class Order implements Serializable {

    @Id
    private Long id;

    @Indexed
    private Date when;

    @Reference
    private List<LineItem> lineItems;

}

@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("lineItems")
class LineItem implements Serializable {
    @Id
    private Long id;

    @Indexed
    private Long orderId;

    private String description;
}

class ShoppingCart implements Serializable {
    private final Collection<Order> orders = new ArrayList<>();

    public void addOrder(Order order) {
        this.orders.add(order);
    }


    public Collection<Order> getOrders() {
        return this.orders;
    }

}


@Log
@Controller
@SessionAttributes("cart")
class CartSessionController {

    private final AtomicLong ids = new AtomicLong();

    @ModelAttribute("cart")
    ShoppingCart cart() {
        log.info("creating new cart");
        return new ShoppingCart();
    }


    @GetMapping("orders")
    String orders(@ModelAttribute("cart") ShoppingCart cart, Model model) {
        cart.addOrder(new Order(ids.incrementAndGet(), new Date(), Collections.emptyList()));
        model.addAttribute("orders", cart.getOrders());
        return "orders";
    }

}