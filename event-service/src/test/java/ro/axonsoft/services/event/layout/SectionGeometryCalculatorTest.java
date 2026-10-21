package ro.axonsoft.services.event.layout;

import org.junit.jupiter.api.Test;
import ro.axonsoft.services.event.layout.SectionGeometryCalculator.Point;
import ro.axonsoft.services.event.layout.SectionGeometryCalculator.SectionGeometry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SectionGeometryCalculatorTest {

    private final SectionGeometryCalculator calculator = new SectionGeometryCalculator();

    private static final int ROWS = 5;
    private static final int SEATS_PER_ROW = 8;

    // A square straddling the x-axis, symmetric about y=0 - its centroid sits
    // exactly on the positive x-axis, so angle comes out bit-exact 0.
    private static final List<Point> EAST_FACING_SQUARE = List.of(
            new Point(150, -50),
            new Point(250, -50),
            new Point(250, 50),
            new Point(150, 50)
    );

    // Same shape, straddling the y-axis above the origin.
    private static final List<Point> NORTH_FACING_SQUARE = List.of(
            new Point(-50, -250),
            new Point(50, -250),
            new Point(50, -150),
            new Point(-50, -150)
    );

    // Same shape, straddling the y-axis below the origin.
    private static final List<Point> SOUTH_FACING_SQUARE = List.of(
            new Point(-50, 150),
            new Point(50, 150),
            new Point(50, 250),
            new Point(-50, 250)
    );

    private SectionGeometry compute(List<Point> vertices) {
        return calculator.compute(vertices, ROWS, SEATS_PER_ROW);
    }

    @Test
    void sectionFacingEastSitsOnThePositiveXAxis() {
        SectionGeometry geometry = compute(EAST_FACING_SQUARE);

        assertThat(geometry.angle()).isEqualTo(0.0);
        assertThat(geometry.originX()).isPositive();
        // No rotation happens for a zero angle, so the perpendicular (y) offset
        // of a symmetric square lands exactly on the axis.
        assertThat(geometry.originY()).isEqualTo(0.0);
    }

    @Test
    void sectionFacingNorthHasNegativeAngleAndSitsAboveTheOrigin() {
        SectionGeometry geometry = compute(NORTH_FACING_SQUARE);

        assertThat(geometry.angle()).isCloseTo(-Math.PI / 2, within(1e-9));
        assertThat(geometry.originY()).isNegative();
        assertThat(geometry.originX()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void sectionFacingSouthHasPositiveAngleAndSitsBelowTheOrigin() {
        SectionGeometry geometry = compute(SOUTH_FACING_SQUARE);

        assertThat(geometry.angle()).isCloseTo(Math.PI / 2, within(1e-9));
        assertThat(geometry.originY()).isPositive();
        assertThat(geometry.originX()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void rowAndSeatRangesAlwaysStartAtOne() {
        SectionGeometry geometry = compute(EAST_FACING_SQUARE);

        assertThat(geometry.rowStart()).isEqualTo(1);
        assertThat(geometry.seatStart()).isEqualTo(1);
    }

    @Test
    void seatCountsComeFromTheRequestNotFromThePolygonSize() {
        SectionGeometry geometry = calculator.compute(EAST_FACING_SQUARE, 12, 30);

        assertThat(geometry.rowEnd()).isEqualTo(12);
        assertThat(geometry.seatEnd()).isEqualTo(30);
    }

    @Test
    void sameCountsInDifferentSizedPolygonsYieldTheSameGrid() {
        List<Point> largeSquare = List.of(
                new Point(150, -200),
                new Point(550, -200),
                new Point(550, 200),
                new Point(150, 200)
        );

        SectionGeometry small = compute(EAST_FACING_SQUARE);
        SectionGeometry large = compute(largeSquare);

        // The grid is identical...
        assertThat(large.rowEnd()).isEqualTo(small.rowEnd());
        assertThat(large.seatEnd()).isEqualTo(small.seatEnd());
        // ...but the bigger outline spreads the same seats further apart.
        assertThat(large.rowSpacing()).isGreaterThan(small.rowSpacing());
    }

    @Test
    void aVeryDenseGridStillGetsUsableSpacing() {
        SectionGeometry geometry = calculator.compute(EAST_FACING_SQUARE, 500, 500);

        assertThat(geometry.rowSpacing()).isPositive();
    }

    @Test
    void rejectsNonPositiveCounts() {
        assertThatThrownBy(() -> calculator.compute(EAST_FACING_SQUARE, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.compute(EAST_FACING_SQUARE, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // A rectangle whose true axis runs east-west (local width along x) - same
    // convention as EAST_FACING_SQUARE - but sitting up at a 60-degree angle
    // from the stage instead of due east. Guessing the angle from the centroid
    // (atan2) gets ~60 degrees here, not the rectangle's real 0-degree axis -
    // exactly the caller-knows-better case explicitAngle exists for.
    private static final List<Point> RECTANGLE_MISALIGNED_WITH_ITS_OWN_CENTROID = List.of(
            new Point(0, 133.205),
            new Point(200, 133.205),
            new Point(200, 213.205),
            new Point(0, 213.205)
    );

    // Same shape (200x80), same true axis (0 degrees), but centered directly on
    // the x-axis - here the centroid guess happens to already equal the true
    // axis, so the old (3-arg, guessed) path gets the right answer by
    // coincidence. Used as the "what the correct, undistorted fit looks like"
    // reference below.
    private static final List<Point> RECTANGLE_ALIGNED_WITH_ITS_OWN_CENTROID = List.of(
            new Point(200, -40),
            new Point(400, -40),
            new Point(400, 40),
            new Point(200, 40)
    );

    @Test
    void explicitAngleRecoversTheFullRectangleEvenWhenTheCentroidGuessWouldBeWrong() {
        SectionGeometry withExplicitAngle = calculator.compute(
                RECTANGLE_MISALIGNED_WITH_ITS_OWN_CENTROID, ROWS, SEATS_PER_ROW, 0.0);
        SectionGeometry undistortedReference = compute(RECTANGLE_ALIGNED_WITH_ITS_OWN_CENTROID);

        // Passing the true angle explicitly recovers exactly the same fit as a
        // rectangle the naive centroid guess would have gotten right anyway -
        // the misalignment with the stage direction stops mattering.
        assertThat(withExplicitAngle.rowSpacing()).isEqualTo(undistortedReference.rowSpacing());
    }

    @Test
    void guessingTheAngleFromTheCentroidUnderfitsARectangleThatIsNotRadiallyPlaced() {
        SectionGeometry guessed = compute(RECTANGLE_MISALIGNED_WITH_ITS_OWN_CENTROID);
        SectionGeometry withExplicitAngle = calculator.compute(
                RECTANGLE_MISALIGNED_WITH_ITS_OWN_CENTROID, ROWS, SEATS_PER_ROW, 0.0);

        // Without the true angle, the rectangle looks tilted once rotated into
        // the (wrongly-guessed) stage frame, so the axis-aligned shrink-fit
        // finds a smaller inscribed box than the rectangle actually is - fewer
        // seats' worth of usable space than the shape really has.
        assertThat(guessed.rowSpacing()).isLessThan(withExplicitAngle.rowSpacing());
    }

    // 200 (row axis) x 100 (seat axis), already aligned with the x-axis - a
    // shape whose length:width ratio (2:1) is the *opposite* of its
    // rows:seatsPerRow ratio (10:20, i.e. 1:2) below. This is exactly the
    // mismatch that under-fills a section when one shared spacing value has
    // to serve both axes - see the col-spacing addition in project memory.
    private static final List<Point> RECTANGLE_WITH_MISMATCHED_ASPECT_RATIO = List.of(
            new Point(0, -50),
            new Point(200, -50),
            new Point(200, 50),
            new Point(0, 50)
    );

    @Test
    void rowAndColSpacingAreIndependentInsteadOfOneDraggingTheOtherDown() {
        SectionGeometry geometry = calculator.compute(RECTANGLE_WITH_MISMATCHED_ASPECT_RATIO, 10, 20, 0.0);

        // rect.width()=~200 along the row axis / 10 rows = ~20; rect.height()=~100
        // along the seat axis / 20 seats = ~5 (not pinned to an exact value - a
        // rectangle that exactly equals its own bounding box can, depending on
        // fitInscribedRectangle's shrink step, land its corners exactly on the
        // polygon's own vertices, an accepted ambiguous case for its point-in-
        // polygon check; see its own "heuristic, not optimal" comment). The
        // point being tested is independence: with one shared spacing, both
        // would have been forced down to ~5, using under a quarter of the
        // available row-axis length.
        assertThat(geometry.rowSpacing()).isGreaterThanOrEqualTo(19);
        assertThat(geometry.colSpacing()).isLessThanOrEqualTo(5);
        assertThat(geometry.rowSpacing()).isGreaterThan(geometry.colSpacing() * 3);
    }

    @Test
    void computeHandlesANonRectangularSection() {
        // Triangle: base near the stage, apex pointing away from it.
        List<Point> triangle = List.of(
                new Point(100, -80),
                new Point(250, 0),
                new Point(100, 80)
        );

        assertThatCode(() -> compute(triangle)).doesNotThrowAnyException();

        SectionGeometry geometry = compute(triangle);
        assertThat(geometry.rowEnd()).isEqualTo(ROWS);
        assertThat(geometry.seatEnd()).isEqualTo(SEATS_PER_ROW);
        assertThat(geometry.angle()).isFinite();
    }
}
