/** Length + reason inputs shared by the ban and mute forms. */
export function PunishmentFields({ reasonPlaceholder = "Reason (shown to other staff)" }: { reasonPlaceholder?: string }) {
  return (
    <>
      <label className="text-sm">
        <span className="block text-xs text-mist-dim">Length</span>
        <select name="days" className="field-select mt-1 w-auto" defaultValue="">
          <option value="1">1 day</option>
          <option value="3">3 days</option>
          <option value="7">7 days</option>
          <option value="30">30 days</option>
          <option value="">Permanent</option>
        </select>
      </label>
      <label className="min-w-48 flex-1 text-sm">
        <span className="block text-xs text-mist-dim">Reason</span>
        <input name="reason" required maxLength={200} placeholder={reasonPlaceholder} className="field mt-1" />
      </label>
    </>
  );
}
