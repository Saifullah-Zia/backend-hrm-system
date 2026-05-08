package com.hrm.system.service;

import com.hrm.system.dto.PositionDto;
import com.hrm.system.model.Department;
import com.hrm.system.model.Position;
import com.hrm.system.repository.DepartmentRepository;
import com.hrm.system.repository.PositionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@Service
public class PositionService {

    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private DepartmentRepository departmentRepository;

    //Entity to Dto
    private PositionDto toDto(Position position) {
        PositionDto dto = new PositionDto();
        dto.setId(position.getId());
        dto.setTitle(position.getTitle());
        dto.setDescription(position.getDescription());


        dto.setDepartmentId(position.getDepartment() != null
                ? position.getDepartment().getId() : null);

        dto.setCreatedAt(position.getCreatedAt());
        dto.setUpdatedAt(position.getUpdatedAt());
        dto.setCreatedBy(position.getCreatedBy());
        dto.setUpdatedBy(position.getUpdatedBy());
        return dto;
    }

    //Dto to Entity
    public Position toEntity(PositionDto dto){
        Position p = new Position();
        p.setTitle(dto.getTitle());
        p.setDescription(dto.getDescription());
        if(dto.getDepartmentId() !=null){
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(()-> new RuntimeException("Department not found"));

            p.setDepartment(dept);
        }
        return p;
    }

    //get All Positions
    public List<PositionDto> getAllPosition() {
        return positionRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    //get Position by departmentId
    public List<PositionDto> getDepartmentById(Long departmentId){
        return positionRepository.findByDepartmentId(departmentId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    //get position by id
    public PositionDto getPositionById(Long id){
        Position position = positionRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Position not found"));
        return toDto(position);

    }

    //create position
    public PositionDto createPosition(PositionDto dto){
     Position position =toEntity(dto);
     return toDto(positionRepository.save(position));
    }

    //Updated Position
    public PositionDto UpdatePosition(Long id, PositionDto dto){
        Position position = positionRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Positon not found on this id"));
        position.setTitle(dto.getTitle());
        position.setDescription(dto.getDescription());
        if(dto.getDepartmentId() !=null){
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(()-> new RuntimeException("Department not found"));
            position.setDepartment(dept);
        }
        return toDto(positionRepository.save(position));

    }

    //delete department
    public void deletePosition(Long id) {
        if (!positionRepository.existsById(id))
            throw new RuntimeException("Position not found");
        positionRepository.deleteById(id);
    }

    }

