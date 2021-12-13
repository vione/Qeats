
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;

import java.io.IOException;
import java.time.LocalTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;
  private static final String FIXTURES = "fixtures/exchanges";
  private int numThreads = 5;
  
  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;

  // TODO: CRIO_TASK_MODULE_RESTAURANTSAPI - Implement findAllRestaurantsCloseby.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(GetRestaurantsRequest 
      getRestaurantsRequest, LocalTime currentTime) {
      
    //GetRestaurantsResponse response = new GetRestaurantsResponse();
    //RestaurantRepositoryService restRepositoryService;
    //= new RestaurantRepositoryServiceDummyImpl();
    Double servingDistance = getServingDistance(currentTime);
    double destLats = getRestaurantsRequest.getLatitude();
    double destLong = getRestaurantsRequest.getLongitude();
    
    return  new GetRestaurantsResponse(restaurantRepositoryService
        .findAllRestaurantsCloseBy(destLats, destLong, currentTime, servingDistance));
  }

  private double getServingDistance(LocalTime localTime) {
    //For peak hours: 8AM - 10AM, 1PM-2PM, 7PM-9PM
    LocalTime morningStart = LocalTime.parse("07:59:59");
    LocalTime morningEnd = LocalTime.parse("10:00:01");
    LocalTime noonStart = LocalTime.parse("12:59:59");
    LocalTime noonEnd = LocalTime.parse("14:00:01");
    LocalTime eveningStart = LocalTime.parse("18:59:59");
    LocalTime eveningEnd = LocalTime.parse("21:00:01");
    if (localTime.isAfter(morningStart) && localTime.isBefore(morningEnd)) {
      return peakHoursServingRadiusInKms;
    } else if (localTime.isAfter(noonStart) && localTime.isBefore(noonEnd)) {
      return peakHoursServingRadiusInKms;
    } else if (localTime.isAfter(eveningStart) && localTime.isBefore(eveningEnd)) {
      return peakHoursServingRadiusInKms;
    }
    return normalHoursServingRadiusInKms;
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Implement findRestaurantsBySearchQuery. The request object has the search string.
  // We have to combine results from multiple sources:
  // 1. Restaurants by name (exact and inexact)
  // 2. Restaurants by cuisines (also called attributes)
  // 3. Restaurants by food items it serves
  // 4. Restaurants by food item attributes (spicy, sweet, etc)
  // Remember, a restaurant must be present only once in the resulting list.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQuery(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {

    List<List<Restaurant>> restaurantLists = new ArrayList<>();
    String searchString = getRestaurantsRequest.getSearchFor();
    Double latitude = getRestaurantsRequest.getLatitude();
    Double longitude = getRestaurantsRequest.getLongitude();
    Double servingRadiusInKms = getServingDistance(currentTime);

    final Long startTime = System.currentTimeMillis();
    if (StringUtils.isEmpty(getRestaurantsRequest.getSearchFor())) {
      return new GetRestaurantsResponse(new ArrayList<>());
    }


    restaurantLists.add(restaurantRepositoryService.findRestaurantsByName(
        latitude, longitude, searchString, currentTime, servingRadiusInKms));
    
    restaurantLists.add(restaurantRepositoryService.findRestaurantsByAttributes(
        latitude, longitude, searchString, currentTime, servingRadiusInKms));
    
    restaurantLists.add(restaurantRepositoryService.findRestaurantsByItemName(
        latitude, longitude, searchString, currentTime, servingRadiusInKms));

    restaurantLists.add((restaurantRepositoryService.findRestaurantsByItemAttributes(
        latitude, longitude, searchString, currentTime, servingRadiusInKms)));
    
    long endTime = System.currentTimeMillis() - startTime;
    System.out.println("Total time taken is " + endTime + " ms.");


    Set<String> restSet = new HashSet<>();
    List<Restaurant> result = new ArrayList<>();
    for (List<Restaurant> list:restaurantLists) {
      for (Restaurant restaurant: list) {
        if (!restSet.contains(restaurant.getRestaurantId())) {
          restSet.add(restaurant.getRestaurantId());
          result.add(restaurant);
        }
      }
    }
    
    return new GetRestaurantsResponse(result);
  }

  // TODO: CRIO_TASK_MODULE_MULTITHREADING
  // Implement multi-threaded version of RestaurantSearch.
  // Implement variant of findRestaurantsBySearchQuery which is at least 1.5x time faster than
  // findRestaurantsBySearchQuery.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQueryMt(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
    final Long startTime = System.currentTimeMillis();
    List<List<Restaurant>> restaurantLists = new ArrayList<>();
    String searchString = getRestaurantsRequest.getSearchFor();
    Double latitude = getRestaurantsRequest.getLatitude();
    Double longitude = getRestaurantsRequest.getLongitude();
    Double servingRadiusInKms = getServingDistance(currentTime);
    
    final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    final List<Future<?>> futures = new ArrayList<>();
    Future<?> future = executor.submit(() -> {
      return this.restaurantRepositoryService.findRestaurantsByName(latitude, longitude, 
          searchString, currentTime, servingRadiusInKms);
    });
    future = executor.submit(() -> {
      return this.restaurantRepositoryService.findRestaurantsByItemAttributes(latitude, longitude, 
          searchString, currentTime, servingRadiusInKms);
    });
    futures.add(future);

    future = executor.submit(() -> {
      return this.restaurantRepositoryService.findRestaurantsByItemName(latitude, longitude, 
          searchString, currentTime, servingRadiusInKms);
    });
    futures.add(future);

    future = executor.submit(() -> {
      return this.restaurantRepositoryService.findRestaurantsByAttributes(latitude, longitude, 
          searchString, currentTime, servingRadiusInKms);
    });
    futures.add(future);

    for (Future<?> futur : futures) {
      try {
        restaurantLists.add((List<Restaurant>)futur.get());
      } catch (Exception e) {
        System.out.println("Can't add the result to the list");
        e.printStackTrace();
      }
    }
    long endTime = System.currentTimeMillis() - startTime;
    System.out.println("Total time taken is " + endTime + " ms.");
    Set<String> restSet = new HashSet<>();
    List<Restaurant> result = new ArrayList<>();
    for (List<Restaurant> list:restaurantLists) {
      for (Restaurant restaurant: list) {
        if (!restSet.contains(restaurant.getRestaurantId())) {
          restSet.add(restaurant.getRestaurantId());
          result.add(restaurant);
        }
      }
    }
    
    return new GetRestaurantsResponse(result);
  }
}

