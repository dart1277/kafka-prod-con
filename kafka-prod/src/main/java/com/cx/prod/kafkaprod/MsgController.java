package com.cx.prod.kafkaprod;

import com.cx.prod.kafkaprod.audio.AudioCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(path = "v1")
@RequiredArgsConstructor
@Slf4j
public class MsgController {
    private final AudioService audioService;
    @PostMapping(path = "send")
    ResponseEntity<String> send(@RequestBody AudioCommand audioCommand) {
        log.warn(audioCommand.toString());

        return ResponseEntity.ok(audioService.send(audioCommand).toString());
    }
}
