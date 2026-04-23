package com.univsitdown.user.service;

import com.univsitdown.user.domain.User;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getUser_존재하는_사용자_조회_성공() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@univ.com", "hash", "김학생", "010-1234-5678", "학생");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserResponse response = userService.getUser(userId);

        assertThat(response.email()).isEqualTo("test@univ.com");
        assertThat(response.name()).isEqualTo("김학생");
    }

    @Test
    void getUser_존재하지않으면_UserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateUser_이름_변경_성공() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@univ.com", "hash", "김학생", null, "학생");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserResponse response = userService.updateUser(userId, new UpdateUserRequest("이름변경", null, null));

        assertThat(response.name()).isEqualTo("이름변경");
    }

    @Test
    void updateUser_존재하지않으면_UserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(userId, new UpdateUserRequest("이름변경", null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
