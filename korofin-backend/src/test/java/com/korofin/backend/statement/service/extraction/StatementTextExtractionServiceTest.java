package com.korofin.backend.statement.service.extraction;

import com.korofin.backend.statement.exception.EmptyStatementTextException;
import com.korofin.backend.statement.exception.UnsupportedStatementFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementTextExtractionServiceTest {

    @Mock
    private StatementTextExtractor pdfExtractor;

    @Mock
    private StatementTextExtractor csvExtractor;

    private StatementTextExtractionService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new StatementTextExtractionService(List.of(pdfExtractor, csvExtractor));
    }

    @Test
    void extractTextDelegatesToTheFirstSupportingExtractor() {
        when(pdfExtractor.supports("extracto.pdf")).thenReturn(false);
        when(csvExtractor.supports("extracto.pdf")).thenReturn(true);
        when(csvExtractor.extract(new byte[]{1, 2, 3}, "pass")).thenReturn("texto extraido");

        String result = service.extractText("extracto.pdf", new byte[]{1, 2, 3}, "pass");

        assertThat(result).isEqualTo("texto extraido");
    }

    @Test
    void extractTextThrowsUnsupportedStatementFileExceptionWhenNoExtractorSupportsTheFile() {
        when(pdfExtractor.supports("extracto.docx")).thenReturn(false);
        when(csvExtractor.supports("extracto.docx")).thenReturn(false);

        assertThatThrownBy(() -> service.extractText("extracto.docx", new byte[0], null))
                .isInstanceOf(UnsupportedStatementFileException.class);
    }

    @Test
    void extractTextThrowsEmptyStatementTextExceptionWhenExtractedTextIsBlank() {
        when(pdfExtractor.supports("extracto.pdf")).thenReturn(true);
        when(pdfExtractor.extract(new byte[]{1}, null)).thenReturn("   ");

        assertThatThrownBy(() -> service.extractText("extracto.pdf", new byte[]{1}, null))
                .isInstanceOf(EmptyStatementTextException.class);
    }

    @Test
    void extractTextThrowsEmptyStatementTextExceptionWhenExtractedTextIsNull() {
        when(pdfExtractor.supports("extracto.pdf")).thenReturn(true);
        when(pdfExtractor.extract(new byte[]{1}, null)).thenReturn(null);

        assertThatThrownBy(() -> service.extractText("extracto.pdf", new byte[]{1}, null))
                .isInstanceOf(EmptyStatementTextException.class);
    }
}
