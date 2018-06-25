package com.example.jpa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryFactoryBean;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@SpringBootApplication
@EnableJpaAuditing
@EnableJpaRepositories(repositoryFactoryBeanClass = EnversRevisionRepositoryFactoryBean.class)
public class JpaApplication {

	public static void main(String[] args) {
		SpringApplication.run(JpaApplication.class, args);
	}

	@Component
	class JpaApplicationWriter implements ApplicationRunner {
		private final EntityManager em;
		private final TransactionTemplate transactionTemplate;
		private final CustomerRepository customerRepository;


		public JpaApplicationWriter(final EntityManager em, final TransactionTemplate transactionTemplate, final CustomerRepository customerRepository) {
			this.em = em;
			this.transactionTemplate = transactionTemplate;
			this.customerRepository = customerRepository;
		}

		@Override
		public void run(final ApplicationArguments args) {

			customerRepository.deleteAll();

			transactionTemplate.execute(transactionStatus -> {

				Stream.of("Dave,Syer;Stephane,Nicole;Brian,Clozel".split(";"))
						.map(name -> name.split(","))
						.forEach(tuple -> em.persist(new Customer(tuple[0], tuple[1], new HashSet<>())));

				return null;
			});

			transactionTemplate.execute(transactionStatus -> {

				TypedQuery<Customer> customers = em.createQuery("select c from Customer c", Customer.class);

				customers.getResultList()
						.forEach(customer -> System.out.println("Typed query result : " + ToStringBuilder.reflectionToString(customer)));

				return null;
			});

			transactionTemplate.execute(transactionStatus -> {

				customerRepository.findAll().forEach(customer -> {

					int countOfOrders = (int) (Math.random() * 5);

					for (int i = 0; i < countOfOrders; i++) {
						Order sku = new Order("sku_" + i);
						customer.getOrders().add(sku);
						customerRepository.save(customer);
						System.out.println("Customer " + customer.getFirst() + "' orders updated with an order : " + ToStringBuilder.reflectionToString(sku) + "!!!");
					}

				});


				return null;
			});

			transactionTemplate.execute(transactionStatus -> {

				customerRepository.findByFirstAndLast("Dave", "Syer")
						.forEach(customer ->
								System.out.println("By findByFirstAndLast: " + ToStringBuilder.reflectionToString(customer)));

				customerRepository.byFullName("Dave", "Syer")
						.forEach(customer -> System.out.println("By byFullName: " + ToStringBuilder.reflectionToString(customer)));

				customerRepository.orderSummary()
						.forEach(orderSummary -> System.out.println("Order summary:  count: " + orderSummary.getCount() + " sku: " + orderSummary.getSku()));

				return null;
			});

			customerRepository.byFullName("Dave", "Syer")
					.forEach(dave -> {

						dave.setFirst("David");

						customerRepository.save(dave);
					});

			customerRepository.byFullName("David", "Syer")
					.forEach(david -> {

						customerRepository.findRevisions(david.getId())
								.forEach(revision ->
										System.out.println("Revision: " + ToStringBuilder.reflectionToString(revision.getMetadata()) +
												" for entity: " + ToStringBuilder.reflectionToString(revision.getEntity())));

					});
		}
	}
}

interface OrderSummary {
	Long getCount();

	String getSku();
}


@Component
class Auditor implements AuditorAware<String> {

	private final String user;

	Auditor(final @Value("${user.name}") String user) {
		this.user = user;
	}

	@Override
	public Optional<String> getCurrentAuditor() {
		return Optional.of(user);
	}
}

interface CustomerRepository extends RevisionRepository<Customer, Long, Integer>, JpaRepository<Customer, Long> {

	Collection<Customer> findByFirstAndLast(String first, String last);

	@Query("select c FROM Customer c where c.first = :f and c.last =:l")
	Collection<Customer> byFullName(@Param("f") String f, @Param("l") String l);

	@Query(nativeQuery = true)
	Collection<OrderSummary> orderSummary();
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
class MappedAuiditableBase {
	@Id
	@GeneratedValue
	private Long id;

	@CreatedDate
	private LocalDateTime created;

	@LastModifiedDate
	private LocalDateTime modified;

	@CreatedBy
	private String creator;

	@LastModifiedBy
	private String modifier;

}


@Audited
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "customers")
@NamedNativeQueries(
		@NamedNativeQuery(name = "Customer.orderSummary", query = "select sku as sku , count(id) as count from orders group by sku")
)
class Customer extends MappedAuiditableBase {

	@Column(name = "first_name")
	private String first;

	@Column(name = "last_name")
	private String last;


	//@NotAudited will exclude entity from auditing
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "customer_fk")
	private Set<Order> orders = new HashSet<>();
}

@Audited
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "orders")
class Order extends MappedAuiditableBase {
	private String sku;
}


@Controller
class CustomerController {

	private final CustomerRepository customerRepository;

	CustomerController(final CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@GetMapping("/customers.view")
	String customers(Model model) {
		model.addAttribute("customers", this.customerRepository.findAll());
		return "customers";
	}
}
