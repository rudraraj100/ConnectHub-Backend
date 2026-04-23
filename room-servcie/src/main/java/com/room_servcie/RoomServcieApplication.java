package com.room_servcie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class RoomServcieApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoomServcieApplication.class, args);
    }
}
