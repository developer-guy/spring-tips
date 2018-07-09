package com.example.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class MicrometerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MicrometerApplication.class, args);

		/*class SlowStatefulThing {
			public int getCustomersLOggendInToSystem() {
				return new Random().nextInt(1000);
			}
		}


		SlowStatefulThing customerService = new SlowStatefulThing();


		CompositeMeterRegistry meterRegistry = new CompositeMeterRegistry();
		meterRegistry.add(new JmxMeterRegistry(null, null));
		meterRegistry.add(new PrometheusMeterRegistry(null));

		MeterRegistry mr = meterRegistry;

		mr.config().commonTags("region", System.getenv("CLOUD_REGION"));

		mr.counter("orders-placed").increment(-123);

		mr.gauge("speed", 55);
		mr.gauge("customers-logged-in", customerService, SlowStatefulThing::getCustomersLOggendInToSystem);

		mr.timer("transform-photo-job").record(Duration.ofMillis(12));
		mr.timer("transform-photo-job").record(() -> System.out.println("hello,world!"));
		String greeting = mr.timer("transform-photo-job").record(() -> "Hello, world");*/
	}

	@Bean
	MeterRegistryCustomizer<MeterRegistry> registryCustomizer(@Value("${REGION:us-west}") String region) {
		return registry -> registry.config().commonTags("region", region);
	}


	@Bean
	MeterFilter meterFilter() {
		return MeterFilter.denyNameStartsWith("jvm"); // any metrics which is starts with jvm will ignored by timeseries database.
	}

	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

	@Bean
	ApplicationRunner runner(MeterRegistry meterRegistry) {
		return args ->
			/*this.executorService.scheduleWithFixedDelay(() ->
					meterRegistry
							.timer("transform-photo-task")
							.record(Duration.ofMillis((long) (Math.random() * 1000))),
									500, 500, TimeUnit.MILLISECONDS);*/


				// Custom one
				this.executorService.scheduleWithFixedDelay(() ->
								Timer
										.builder("transform-photo-task")
										.sla(Duration.ofMillis(1), Duration.ofSeconds(10))
										.publishPercentileHistogram()
										.tag("format", Math.random() > .5 ? "png" : "jpg")
										.register(meterRegistry)
										.record(Duration.ofMillis((long) (Math.random() * 1000))),
						500, 500, TimeUnit.MILLISECONDS);
	}

}
