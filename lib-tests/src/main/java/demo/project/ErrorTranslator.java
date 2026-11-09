package demo.project;

import org.springframework.stereotype.Service;

@Service
public class ErrorTranslator {

    private final ExceptionsProvider provider;

    public ErrorTranslator(ExceptionsProvider provider) {
        this.provider = provider;
    }

    public String translate(String code) {
        return "[" + provider.messageFor(code) + "]";
    }
}
