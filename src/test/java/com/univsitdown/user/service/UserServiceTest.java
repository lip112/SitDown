package com.univsitdown.user.service;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.user.domain.Affiliation;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
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

    @TempDir
    private Path tempDir;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create("test@univ.com", "hash", "김학생", "010-1234-5678", Affiliation.UNDERGRADUATE);
        ReflectionTestUtils.setField(userService, "uploadDir", tempDir.toString());
    }

    @Test
    void getUser_존재하는_사용자_조회_성공() {
        UUID userId = UUID.randomUUID();
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
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserResponse response = userService.updateUser(userId, new UpdateUserRequest("이름변경", null, null));

        assertThat(response.name()).isEqualTo("이름변경");
        assertThat(response.email()).isEqualTo("test@univ.com");    // unchanged
        assertThat(response.affiliation()).isEqualTo("UNDERGRADUATE"); // unchanged
    }

    @Test
    void updateUser_존재하지않으면_UserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(userId, new UpdateUserRequest("이름변경", null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUsers_회원목록을_페이지로_조회한다() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(userRepository.findAll(pageable))
                .willReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(user), pageable, 1));

        var response = userService.getUsers(pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).email()).isEqualTo("test@univ.com");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void deleteUser_존재하는_사용자를_삭제한다() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        userService.deleteUser(userId);

        then(userRepository).should().delete(user);
    }

    @Test
    void deleteUser_존재하지않으면_UserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(UserNotFoundException.class);
        then(userRepository).should(never()).delete(any());
    }

    @Test
    void updateProfileImage_이미지파일이_아니면_BusinessException() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile htmlFile = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", "<!DOCTYPE html><html></html>".getBytes());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfileImage(userId, htmlFile))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미지 파일만 업로드할 수 있습니다.");
    }
}
