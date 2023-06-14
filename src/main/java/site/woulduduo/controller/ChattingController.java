package site.woulduduo.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
@CrossOrigin("http://localhost:3000")
@RequestMapping("/chat")
public class ChattingController {

    @GetMapping
    public String test(){
        log.info("으아아아");
        return "index";
    }

}