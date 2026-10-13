package com.freightfox.chatapp;

import com.freightfox.chatapp.controller.ChatRoomController;
import com.freightfox.chatapp.dto.MessageDto;
import com.freightfox.chatapp.exception.DuplicateRoomNameException;
import com.freightfox.chatapp.exception.RoomNotFoundException;
import com.freightfox.chatapp.service.ChatRoomService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @Test
    void createRoomReturns201() throws Exception {
        when(chatRoomService.createRoom("general")).thenReturn("general");

        mockMvc.perform(post("/api/chatapp/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"general\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.roomId").value("general"));
    }

    @Test
    void createRoomRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/chatapp/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoomReturns409ProblemDetailOnDuplicate() throws Exception {
        when(chatRoomService.createRoom("general")).thenThrow(new DuplicateRoomNameException("general"));

        mockMvc.perform(post("/api/chatapp/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"general\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void joinRoomReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/chatapp/chatrooms/general/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"sandy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void joinRoomReturns404ProblemDetailWhenMissing() throws Exception {
        doThrow(new RoomNotFoundException("ghost")).when(chatRoomService).joinRoom(eq("ghost"), any());

        mockMvc.perform(post("/api/chatapp/chatrooms/ghost/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"sandy\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void sendMessageReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/chatapp/chatrooms/general/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"sandy\",\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void sendMessageRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chatapp/chatrooms/general/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"sandy\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMessagesReturnsHistory() throws Exception {
        MessageDto message = new MessageDto("sandy", "hello", "2024-01-01T10:00:00Z");
        when(chatRoomService.getMessages("general", null)).thenReturn(List.of(message));

        mockMvc.perform(get("/api/chatapp/chatrooms/general/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].participant").value("sandy"))
                .andExpect(jsonPath("$.messages[0].message").value("hello"));
    }

    @Test
    void getMessagesReturns404ProblemDetailWhenMissing() throws Exception {
        when(chatRoomService.getMessages(eq("ghost"), any())).thenThrow(new RoomNotFoundException("ghost"));

        mockMvc.perform(get("/api/chatapp/chatrooms/ghost/messages"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMessagesRejectsNonPositiveLimit() throws Exception {
        when(chatRoomService.getMessages("general", 0))
                .thenThrow(new IllegalArgumentException("limit must be a positive integer"));

        mockMvc.perform(get("/api/chatapp/chatrooms/general/messages?limit=0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRoomReturnsSuccess() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/chatapp/chatrooms/general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void deleteRoomReturns404ProblemDetailWhenMissing() throws Exception {
        org.mockito.Mockito.doThrow(new RoomNotFoundException("ghost"))
                .when(chatRoomService).deleteRoom("ghost");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/chatapp/chatrooms/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}