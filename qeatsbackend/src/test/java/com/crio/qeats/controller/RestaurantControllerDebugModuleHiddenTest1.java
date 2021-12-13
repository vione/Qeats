// CRIO_SOLUTION_START_MODULE_DEBUG_V2

/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.controller;

import static com.crio.qeats.controller.RestaurantController.RESTAURANTS_API;
import static com.crio.qeats.controller.RestaurantController.RESTAURANT_API_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.crio.qeats.QEatsApplication;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import com.crio.qeats.services.RestaurantService;
import com.crio.qeats.utils.FixtureHelpers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.mock.mockito.SpyBeans;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {QEatsApplication.class})
@DirtiesContext
@AutoConfigureMockMvc
@SpyBeans(value = {@SpyBean(RestaurantService.class), @SpyBean(RestaurantRepositoryService.class),
    @SpyBean(RestaurantController.class)})
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ActiveProfiles("test")
public class RestaurantControllerDebugModuleHiddenTest1 {

  
  private static final String RESTAURANT_API_URI = RESTAURANT_API_ENDPOINT + RESTAURANTS_API;

  private static final String FIXTURES = "fixtures/exchanges";
  private ObjectMapper objectMapper;

  private MockMvc mvc;

  @Autowired
  private RestaurantService restaurantService;

  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;

  @Autowired
  private RestaurantController restaurantController;

  @BeforeEach
  public void setup() {
    objectMapper = new ObjectMapper();
    MockitoAnnotations.initMocks(this);
    mvc = MockMvcBuilders.standaloneSetup(restaurantController).build();
  }

  @Test
  public void hiddenTest2() throws Exception {
    // Sample response
    GetRestaurantsResponse sampleResponse = loadSampleResponseList();
    assertNotNull(sampleResponse);

    doReturn(sampleResponse.getRestaurants()).when(restaurantRepositoryService)
        .findAllRestaurantsCloseBy(anyDouble(), anyDouble(), any(LocalTime.class), anyDouble());

    ArgumentCaptor<GetRestaurantsRequest> argumentCaptor = ArgumentCaptor
        .forClass(GetRestaurantsRequest.class);

    URI uri = UriComponentsBuilder
        .fromPath(RESTAURANT_API_URI)
        .queryParam("latitude", "20.21")
        .queryParam("longitude", "30.31")
        .build().toUri();

    assertEquals(RESTAURANT_API_URI + "?latitude=20.21&longitude=30.31", uri.toString());

    MockHttpServletResponse response = mvc.perform(
        get(uri.toString()).accept(APPLICATION_JSON_UTF8)
    ).andReturn().getResponse();

    assertEquals(HttpStatus.OK.value(), response.getStatus());
    verify(restaurantService, times(1))
        .findAllRestaurantsCloseBy(argumentCaptor.capture(), any(LocalTime.class));

    assertEquals("20.21", argumentCaptor.getValue().getLatitude().toString());

    assertEquals("30.31", argumentCaptor.getValue().getLongitude().toString());

    GetRestaurantsResponse returnedResponse = objectMapper
        .readValue(response.getContentAsString(), GetRestaurantsResponse.class);
    assertTrue(returnedResponse.getRestaurants().size() > 4000);
    assertFalse(returnedResponse.getRestaurants().get(0).getName().contains("é"));
  }


  private GetRestaurantsResponse loadSampleResponseList() throws IOException {
    String fixture =
        FixtureHelpers.fixture(FIXTURES + "/list_restaurant_response.json");

    GetRestaurantsResponse getRestaurantsResponse = objectMapper.readValue(fixture,
        new TypeReference<GetRestaurantsResponse>() {
        });

    List<Restaurant> contents = getRestaurantsResponse.getRestaurants();
    Restaurant restaurantWithSpecialChar = contents.get(0);
    restaurantWithSpecialChar.setName(restaurantWithSpecialChar.getName() + "é");
    for (int i = 0; i < 12; i++) {
      contents.addAll(contents);
    }
    return new GetRestaurantsResponse(contents);
  }
}

