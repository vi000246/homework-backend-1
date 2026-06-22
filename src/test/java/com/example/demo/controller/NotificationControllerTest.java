package com.example.demo.controller;

import com.example.demo.domain.Notification;
import com.example.demo.domain.NotificationType;
import com.example.demo.exception.NotFoundException;
import com.example.demo.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    NotificationService service;

    private Notification sample(long id) {
        Notification n = new Notification();
        n.setId(id);
        n.setType(NotificationType.EMAIL);
        n.setRecipient("u@e.com");
        n.setSubject("s");
        n.setContent("c");
        n.setCreatedAt(Instant.now());
        n.setUpdatedAt(Instant.now());
        return n;
    }

    @Test
    void create_returns201() throws Exception {                       // AC-1
        when(service.create(any())).thenReturn(sample(1L));
        mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"email\",\"recipient\":\"u@e.com\",\"subject\":\"s\",\"content\":\"c\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_invalidType_returns400() throws Exception {           // AC-2
        when(service.create(any())).thenThrow(new IllegalArgumentException("unknown notification type: fax"));
        mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"fax\",\"recipient\":\"u@e.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingRecipient_returns400() throws Exception {      // AC-2 (validation)
        mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200() throws Exception {
        when(service.getById(1L)).thenReturn(sample(1L));
        mvc.perform(get("/notifications/1")).andExpect(status().isOk());
    }

    @Test
    void getById_missing_returns404() throws Exception {              // AC-4
        when(service.getById(999L)).thenThrow(new NotFoundException("not found"));
        mvc.perform(get("/notifications/999")).andExpect(status().isNotFound());
    }

    @Test
    void recent_returns200List() throws Exception {                   // AC-5
        when(service.recent()).thenReturn(List.of(sample(1L), sample(2L)));
        mvc.perform(get("/notifications/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void update_missing_returns404() throws Exception {               // AC-7
        when(service.update(eq(999L), any())).thenThrow(new NotFoundException("not found"));
        mvc.perform(put("/notifications/999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {                       // AC-8
        doNothing().when(service).delete(1L);
        mvc.perform(delete("/notifications/1")).andExpect(status().isNoContent());
    }

    @Test
    void delete_missing_returns404() throws Exception {               // AC-9
        doThrow(new NotFoundException("not found")).when(service).delete(999L);
        mvc.perform(delete("/notifications/999")).andExpect(status().isNotFound());
    }
}
