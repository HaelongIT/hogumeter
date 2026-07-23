package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.AddListingNoteUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** USED-05 AC-16 자유 메모 REST. */
@RestController
@RequestMapping("/api/v1/listings/{listingId}/notes")
public class ListingNoteController {

	private final AddListingNoteUseCase useCase;

	public ListingNoteController(AddListingNoteUseCase useCase) {
		this.useCase = useCase;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public NoteCreated create(@PathVariable long listingId, @RequestBody NoteRequest req) {
		return new NoteCreated(useCase.addNote(listingId, req.body()));
	}

	public record NoteRequest(String body) {
	}

	public record NoteCreated(long noteId) {
	}
}
