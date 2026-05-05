package com.hrm.system.controller;

import com.hrm.system.dto.UserDTO;
import com.hrm.system.model.User;
import com.hrm.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    //Create User
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody User user){
        if(user.getPassword() == null){
            throw new RuntimeException("Password is null");
        }
        return new ResponseEntity<>(userService.createUser(user), HttpStatus.CREATED);
    }

    //Get all users
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers(){
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    //Get user by id
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id){
        UserDTO user =userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    //Get user by email
    @GetMapping("/email/{email}")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email){
        UserDTO user =userService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }

    //Update user
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody User user){
        UserDTO updatedUser =userService.updateUser(id, user);
        return ResponseEntity.ok(updatedUser);

    }

    //Delete user
    @DeleteMapping("/{id}")
    public ResponseEntity <Map<String, String>> deleteUser(@PathVariable Long id){
        userService.deleteUser(id);

        Map<String, String> response =new HashMap<>();
        response.put("message","User deleted successfully");
        return ResponseEntity.ok(response);
    }

    //Change password
    @PutMapping("/{id}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Long id, @RequestBody Map<String ,String> passwordRequest){

        String oldPassword = passwordRequest.get("oldPassword");
        String newPassword = passwordRequest.get("newPassword");

        userService.changePassword(id, oldPassword, newPassword);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password changed successfully");
        return ResponseEntity.ok(response);

    }
}