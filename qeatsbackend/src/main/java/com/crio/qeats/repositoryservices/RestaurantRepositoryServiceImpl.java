/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.ItemEntity;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.ItemRepository;
import com.crio.qeats.repositories.MenuRepository;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import javax.print.DocFlavor.READER;
import javax.xml.transform.Result;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private MenuRepository menuRepository;

  @Autowired
  private ItemRepository itemRepository;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  @Autowired
  private RedisConfiguration redisConfiguration;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objectives:
  // 1. Implement findAllRestaurantsCloseby.
  // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  // Check RestaurantRepositoryService.java file for the interface contract.
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude, Double longitude, 
      LocalTime currentTime, Double servingRadiusInKms) {

    GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, 7);
    List<Restaurant> restaurants = new ArrayList<>();
    JedisPool jedisPool = redisConfiguration.getJedisPool();
    Jedis resource = jedisPool.getResource();
    String hash = geoHash.toBase32();
    String hash2 = hash + currentTime.getHour() + ":" + currentTime.getMinute();
    ObjectMapper objectMapper = new ObjectMapper();
    if (resource.get(hash) == null || resource.get(hash2) == null) {
      List<RestaurantEntity> restaurantEntities = restaurantRepository.findAll();
      for (RestaurantEntity restaurantEntity : restaurantEntities) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude,
            longitude, servingRadiusInKms)) {
          ModelMapper modelMapper = modelMapperProvider.get();
          Restaurant restaurant = modelMapper.map(restaurantEntity, Restaurant.class);
          restaurants.add(restaurant);
        }
      }
      try {
        String result = objectMapper.writeValueAsString(restaurants);
        resource.set(hash,result);
        resource.set(hash2,result);
      } catch (JsonProcessingException e) {
        System.out.println("Json Parsing Exception");
        e.printStackTrace();
      }
    } else {
      try {
        List<Restaurant> rest = objectMapper.readValue(resource.get(hash2), 
            new TypeReference<List<Restaurant>>() {});
        restaurants.addAll(rest);
      } catch (Exception e) {
        System.out.println("Error while Serializing cache result");
        e.printStackTrace();
      }
    }
    return restaurants;
  }








  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objective:
  // 1. Check if a restaurant is nearby and open. If so, it is a candidate to be returned.
  // NOTE: How far exactly is "nearby"?

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Restaurant> resultList = new ArrayList<>();
    ModelMapper modelMapper = modelMapperProvider.get();
    Set<String> restaurantSet = new HashSet<>();

    Optional<List<RestaurantEntity>> optionalRestaurantExactList = restaurantRepository
        .findRestaurantsByNameExact(searchString);
    if (optionalRestaurantExactList.isPresent()) {
      List<RestaurantEntity> restaurantEntities = optionalRestaurantExactList.get();
      for (RestaurantEntity entity: restaurantEntities) {
        if (isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, 
            servingRadiusInKms) && !restaurantSet.contains(entity.getRestaurantId())) {
          resultList.add(modelMapper.map(entity, Restaurant.class));
          restaurantSet.add(entity.getRestaurantId());
        }
      }
    }

    Optional<List<RestaurantEntity>> optionalRestaurantNonExactList = restaurantRepository
        .findRestaurantsByName(searchString);
    if (optionalRestaurantNonExactList.isPresent()) {
      List<RestaurantEntity> restaurantEntities = optionalRestaurantNonExactList.get();
      for (RestaurantEntity entity: restaurantEntities) {
        if (isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, 
            servingRadiusInKms) && !restaurantSet.contains(entity.getRestaurantId())) {
          resultList.add(modelMapper.map(entity, Restaurant.class));
          restaurantSet.add(entity.getRestaurantId());
        }
      }
    }
    return resultList;
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    
    List<Restaurant> resultList = new ArrayList<>();
    ModelMapper modelMapper = modelMapperProvider.get();
    List<Pattern> patterns = Arrays.stream(searchString.split(" "))
        .map(attr -> Pattern.compile(attr,Pattern.CASE_INSENSITIVE))
        .collect(Collectors.toList());
    Query query = new Query();
    for (Pattern pattern: patterns) {
      query.addCriteria(Criteria.where("attributes").regex(pattern));
    }

    List<RestaurantEntity> restaurantEntities = mongoTemplate.find(query, RestaurantEntity.class);
    if (restaurantEntities.size() > 0) {
      System.out.println("There were some results at least");
      //List<RestaurantEntity> restaurantEntities = optionalRestaurantEntityList.get();
      for (RestaurantEntity entity: restaurantEntities) {
        //System.out.println(entity.getName());
        if (isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, 
            servingRadiusInKms)) {
          resultList.add(modelMapper.map(entity, Restaurant.class));
        }
      }
    }

    return resultList;
  }



  // INFO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.

  @Override
  public List<Restaurant> findRestaurantsByItemName(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    
    String itemRegex = String.join("|", Arrays.asList(searchString.split(" ")));
    Optional<List<ItemEntity>> exactMatchList = itemRepository.findItemsByNameExact(searchString);
    Optional<List<ItemEntity>> notExactMatchList = itemRepository.findItemsByNameInexact(itemRegex);

    List<ItemEntity> exactList = exactMatchList.orElseGet(ArrayList::new);
    List<ItemEntity> notExactList = notExactMatchList.orElseGet(ArrayList::new);
    exactList.addAll(notExactList);

    return getRestaurantfromItem(latitude, longitude, currentTime, servingRadiusInKms, 
        exactList);
  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Pattern> patterns = Arrays.stream(searchString.split(" "))
        .map(attr -> Pattern.compile(attr,Pattern.CASE_INSENSITIVE))
        .collect(Collectors.toList());
    Query query = new Query();
    for (Pattern pattern: patterns) {
      query.addCriteria(Criteria.where("attributes").regex(pattern));
    }

    List<ItemEntity> itemEntities = mongoTemplate.find(query, ItemEntity.class);

    return getRestaurantfromItem(latitude, longitude, currentTime,
        servingRadiusInKms, itemEntities);
  }





  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude,
          restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
          < servingRadiusInKms;
    }

    return false;
  }

  //Function to return the Restaurants containing the item
  private List<Restaurant> getRestaurantfromItem(
      Double latitude, Double longitude, LocalTime currentTime, Double servingRadiusInKms,
      List<ItemEntity> itemEntities) {
    List<Restaurant> resultList = new ArrayList<>();
    List<String> itemIdList = itemEntities.stream()
          .map(ItemEntity::getItemId).collect(Collectors.toList());
    Optional<List<MenuEntity>> menuEntities = menuRepository.findMenusByItemsItemIdIn(itemIdList);
    Optional<List<RestaurantEntity>> optionalRestaurantList = Optional.empty();

    if (menuEntities.isPresent()) {
      List<MenuEntity> menuEntityList = menuEntities.get();
      List<String> restaurantIds = menuEntityList.stream()
          .map(MenuEntity::getRestaurantId).collect(Collectors.toList());
      optionalRestaurantList = restaurantRepository.findRestaurantsByRestaurantIdIn(restaurantIds);
    }

    if (optionalRestaurantList.isPresent()) {
      List<RestaurantEntity> restaurantEntities =  optionalRestaurantList.get();
      ModelMapper modelMapper = modelMapperProvider.get();
      for (RestaurantEntity entity: restaurantEntities) {
        if (isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, 
            servingRadiusInKms)) {
          resultList.add(modelMapper.map(entity, Restaurant.class));
        }
      }
    }

    return resultList;
  }



}

