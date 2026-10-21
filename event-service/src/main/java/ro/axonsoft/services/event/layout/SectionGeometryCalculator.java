package ro.axonsoft.services.event.layout;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Computes a section's rendering geometry from its drawn outline polygon. The
 * stage/scene center is always the fixed origin (0, 0); a section's "facing
 * angle" is the direction rows extend away from the origin, seats within a row
 * spreading along the perpendicular axis (handled client-side - see the
 * frontend's geometry.js, which uses the same convention to place individual
 * seats). When the caller already knows this angle (a Rectangle/Trapezoid built
 * from frontend shape state, possibly rotated by the admin) it's taken as given;
 * otherwise (freeform, orientation genuinely unknown from raw points) it's
 * guessed from the direction to the polygon's centroid - generalizing the old
 * fixed NORTH/SOUTH/EAST/WEST directions to an arbitrary angle. See the two
 * {@code compute} overloads below.
 *
 * <p>Write-time-only: this runs once inside {@code VenueSeedingService.seedVenue()}
 * and its output is persisted onto {@code Section}'s columns. The read path
 * ({@code VenueLayoutService}) just reads those columns back - it no longer
 * recomputes geometry on every request.
 */
@Component
public class SectionGeometryCalculator {

    private static final int SHRINK_STEPS = 20;
    private static final int MIN_SPACING = 1;

    public record Point(double x, double y) {}

    private Point centroid(List<Point> vertices) {
        double sumX = vertices.stream().mapToDouble(Point::x).sum();
        double sumY = vertices.stream().mapToDouble(Point::y).sum();

        return new Point(sumX / vertices.size(), sumY / vertices.size());
    }

    private double guessAngleFromCentroid(List<Point> vertices) {
        Point centroid = centroid(vertices);
        return Math.atan2(centroid.y(), centroid.x());
    }

    private List<Point> rotateToStageFrame(List<Point> vertices, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vertices.stream()
                .map(p -> new Point(
                        p.x() * cos + p.y() * sin,
                        -p.x() * sin + p.y() * cos))
                .toList();
    }

    // Freeform path: orientation is genuinely unknown from raw points alone, so
    // guess it from the polygon's centroid (direction from the stage origin to
    // where the section sits) - the best available default when nothing better
    // was drawn/rotated with a known angle. See the explicit-angle overload
    // below for the case where the caller already knows the true angle.
    public SectionGeometry compute(List<Point> vertices, int rows, int seatsPerRow) {
        return compute(vertices, rows, seatsPerRow, null);
    }

    // explicitAngle: pass the section's real, already-known facing angle (radians)
    // when the caller has one - e.g. a Rectangle/Trapezoid built from frontend
    // shape state that was explicitly rotated. Using the true angle instead of
    // guessing it from the centroid matters once a shape can be rotated to face
    // any direction: guessing from the centroid rotates the polygon by the wrong
    // amount, so fitInscribedRectangle finds it no longer axis-aligned and shrinks
    // down from a perfectly good rectangle/trapezoid to a smaller inscribed box.
    // Pass null to fall back to the centroid guess (the freeform behavior above).
    public SectionGeometry compute(List<Point> vertices, int rows, int seatsPerRow, Double explicitAngle) {
        if (rows < 1 || seatsPerRow < 1) {
            throw new IllegalArgumentException(
                    "rows and seatsPerRow must be at least 1, got " + rows + " and " + seatsPerRow);
        }

        double angle = explicitAngle != null ? explicitAngle : guessAngleFromCentroid(vertices);

        List<Point> rotated = rotateToStageFrame(vertices, angle);
        Rectangle rect = fitInscribedRectangle(rotated);

        // Independent spacing per axis - rect.width() is the fitted rectangle's
        // extent along the row axis (rotated-frame x, "toward/away from the
        // stage"), rect.height() along the seat axis (rotated-frame y,
        // perpendicular). Using one shared value for both used to force
        // whichever axis was tighter onto the other, badly under-filling a
        // section whose length:width ratio didn't happen to match its
        // rows:seatsPerRow ratio - see the col-spacing addition in project memory.
        int rowSpacing = Math.max(MIN_SPACING, (int) Math.floor(rect.width() / rows));
        int colSpacing = Math.max(MIN_SPACING, (int) Math.floor(rect.height() / seatsPerRow));

        // Anchor for rowStart: the rectangle's edge nearest the stage (min x in the
        // rotated frame), centered on the seat axis (mid y) - same convention
        // lib/geometry.ts's seatPosition() uses today, generalized from a 4-way
        // switch to an arbitrary angle. Rotating by -angle undoes the earlier
        // rotation, bringing the anchor back into real venue-space coordinates.
        Point anchorRotated = new Point(rect.x(), rect.y() + rect.height() / 2);
        Point anchor = rotateToStageFrame(List.of(anchorRotated), -angle).get(0);

        return new SectionGeometry(
                angle,
                anchor.x(),
                anchor.y(),
                rowSpacing,
                colSpacing,
                1,
                rows,
                1,
                seatsPerRow
        );
    }

    // Shrinks the rotated bounding box inward, in fixed steps, until all four
    // corners land inside the polygon. Heuristic, not an optimal
    // largest-inscribed-rectangle solve (that has no closed form) - accepted
    // trade-off. Must run on the same rotated points rotateToStageFrame produced.
    private Rectangle fitInscribedRectangle(List<Point> rotatedVertices) {
        double minX = rotatedVertices.stream().mapToDouble(Point::x).min().orElseThrow();
        double maxX = rotatedVertices.stream().mapToDouble(Point::x).max().orElseThrow();
        double minY = rotatedVertices.stream().mapToDouble(Point::y).min().orElseThrow();
        double maxY = rotatedVertices.stream().mapToDouble(Point::y).max().orElseThrow();

        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        double halfWidth = (maxX - minX) / 2;
        double halfHeight = (maxY - minY) / 2;

        for (int step = 0; step <= SHRINK_STEPS; step++) {
            double factor = 1.0 - (double) step / SHRINK_STEPS;
            double hw = halfWidth * factor;
            double hh = halfHeight * factor;

            List<Point> corners = List.of(
                    new Point(centerX - hw, centerY - hh),
                    new Point(centerX + hw, centerY - hh),
                    new Point(centerX + hw, centerY + hh),
                    new Point(centerX - hw, centerY + hh));

            if (corners.stream().allMatch(c -> isInsidePolygon(c, rotatedVertices))) {
                return new Rectangle(centerX - hw, centerY - hh, hw * 2, hh * 2);
            }
        }
        // Degenerate fallback: polygon too thin/concave for any step to fit - a
        // single point at the centroid, which still produces a valid (if trivial,
        // 0-row) seat grid rather than throwing.
        return new Rectangle(centerX, centerY, 0, 0);
    }

    // Standard even-odd ray-casting point-in-polygon test.
    private boolean isInsidePolygon(Point point, List<Point> polygon) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point pi = polygon.get(i);
            Point pj = polygon.get(j);
            boolean crosses = (pi.y() > point.y()) != (pj.y() > point.y())
                    && point.x() < (pj.x() - pi.x()) * (point.y() - pi.y()) / (pj.y() - pi.y()) + pi.x();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    private record Rectangle(double x, double y, double width, double height) {}

    public record SectionGeometry(
            double angle,
            double originX,
            double originY,
            int rowSpacing,
            int colSpacing,
            int rowStart,
            int rowEnd,
            int seatStart,
            int seatEnd
    ) {}
}
