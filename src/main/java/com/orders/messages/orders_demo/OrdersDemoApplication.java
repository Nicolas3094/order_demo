package com.orders.messages.orders_demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrdersDemoApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(OrdersDemoApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Aplicacion inicializada");
	}

}
