package io.pillopl.library.lending.application.hold

import io.pillopl.library.lending.domain.book.BookOnHold
import io.pillopl.library.lending.domain.patron.PatronBooks
import io.pillopl.library.lending.domain.patron.PatronBooksFixture
import io.pillopl.library.lending.domain.patron.PatronBooksRepository
import io.pillopl.library.lending.domain.patron.PatronId
import io.vavr.control.Option
import io.vavr.control.Try
import spock.lang.Specification

import java.time.Instant

import static io.pillopl.library.lending.application.hold.CancelingHold.Result.Success
import static io.pillopl.library.lending.domain.book.BookFixture.anyBookId
import static io.pillopl.library.lending.domain.book.BookFixture.bookOnHold
import static io.pillopl.library.lending.domain.library.LibraryBranchFixture.anyBranch
import static io.pillopl.library.lending.domain.patron.PatronBooksFixture.anyPatronId

class CancelingHoldTest extends Specification {

    BookOnHold bookOnHold = bookOnHold()
    PatronId patronId = anyPatronId()

    FindBookOnHold willFindBook = { bookId, patronId -> Option.of(bookOnHold) }
    FindBookOnHold willNotFindBook = { bookId, patronId -> Option.none() }
    PatronBooksRepository repository = Stub()

    def 'should successfully cancel hold if book was placed on hold by patron and patron and book exist'() {
        given:
            CancelingHold canceling = new CancelingHold(willFindBook, repository)
        and:
            persistedRegularPatronWithBookOnHold()
        when:
            Try<CancelingHold.Result> result = canceling.cancelHold(cmd())
        then:
            result.isSuccess()
            result.get() == Success
    }

    def 'should reject placing on hold book if one of the domain rules is broken (but should not fail!)'() {
        given:
            CancelingHold canceling = new CancelingHold(willFindBook, repository)
        and:
            persistedRegularPatronWithoutBookOnHold()
        when:
            Try<CancelingHold.Result> result = canceling.cancelHold(cmd())
        then:
            result.isSuccess()
            result.get() == CancelingHold.Result.Rejection

    }

    def 'should fail if patron does not exists'() {
        given:
            CancelingHold canceling = new CancelingHold(willFindBook, repository)
        and:
            unknownPatron()
        when:
            Try<CancelingHold.Result> result = canceling.cancelHold(cmd())
        then:
            result.isFailure()

    }

    def 'should fail if book does not exists'() {
        given:
            CancelingHold canceling = new CancelingHold(willNotFindBook, repository)
        and:
            persistedRegularPatronWithBookOnHold()
        when:
            Try<CancelingHold.Result> result = canceling.cancelHold(cmd())
        then:
            result.isFailure()
    }

    def 'should reject (but not fail) if saving patron fails'() {
        given:
            CancelingHold canceling = new CancelingHold(willFindBook, repository)
        and:
            persistedRegularPatronThatFailsOnSaving()
        when:
            Try<CancelingHold.Result> result = canceling.cancelHold(cmd())
        then:
            result.isSuccess()
            result.get() == CancelingHold.Result.Rejection

    }

    CancelHoldCommand cmd() {
        return new CancelHoldCommand(Instant.now(), patronId, anyBranch(), anyBookId())
    }

    PatronId persistedRegularPatronWithBookOnHold() {
        PatronBooks patron = PatronBooksFixture.regularPatronWithHold(bookOnHold)
        repository.findBy(patronId) >> Option.of(patron)
        repository.handle(_) >> Try.success(patron)
        return patronId
    }

    PatronId persistedRegularPatronWithoutBookOnHold() {
        PatronBooks patron = PatronBooksFixture.regularPatronWithHolds(10)
        repository.findBy(patronId) >> Option.of(patron)
        return patronId
    }

    PatronId persistedRegularPatronThatFailsOnSaving() {
        PatronBooks patron = PatronBooksFixture.regularPatron(patronId)
        repository.findBy(patronId) >> Option.of(patron)
        repository.handle(_) >> Try.failure(new IllegalStateException())
        return patronId
    }

    PatronId unknownPatron() {
        return anyPatronId()
    }

}

